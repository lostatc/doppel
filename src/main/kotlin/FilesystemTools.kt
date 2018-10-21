package diffir

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlin.streams.toList

/**
 * Returns a list of paths representing the immediate children of [directory] in the filesystem.
 */
internal fun scanChildren(directory: DirPath): List<MutableFSPath> {
    return Files.list(directory.toPath()).map {
        when {
            Files.isDirectory(it) -> MutableDirPath(it.fileName)
            else -> MutableFilePath(it.fileName)
        }
    }.toList()
}

/**
 * Copies basic file attributes from [source] to [target].
 */
internal fun copyFileAttributes(source: Path, target: Path) {
    val mtime = Files.getLastModifiedTime(source)
    Files.setLastModifiedTime(target, mtime)
}

/**
 * An algorithm used to create a message digest.
 *
 * @property [algorithmName] The name of the algorithm.
 */
enum class DigestAlgorithm(val algorithmName: String) {
    /**
     * The MD5 message digest algorithm as defined in [RFC 1321][http://www.ietf.org/rfc/rfc1319.txt].
     */
    MD5("MD5"),

    /**
     * The SHA-1 hash algorithm defined in the [FIPS PUB 180-2][https://csrc.nist.gov/publications/fips].
     */
    SHA1("SHA-1"),

    /**
     * The SHA-256 hash algorithm defined in the [FIPS PUB 180-2][https://csrc.nist.gov/publications/fips].
     */
    SHA256("SHA-256")
}

/**
 * This is the size of the buffer used when computing the checksum of a file.
 */
private const val CHECKSUM_BUFFER_SIZE: Int = 4096

/**
 * This function computes and returns a checksum of the given [file] using the given [algorithm].
 */
fun getFileChecksum(file: Path, algorithm: DigestAlgorithm = DigestAlgorithm.SHA256): ByteArray {
    val messageDigest = MessageDigest.getInstance(algorithm.algorithmName)
    val inputStream = Files.newInputStream(file)
    val buffer = ByteArray(CHECKSUM_BUFFER_SIZE)

    DigestInputStream(inputStream, messageDigest).use {
        do {
            val bytesRead = it.read(buffer)
        } while (bytesRead != -1)
    }

    return messageDigest.digest()
}

/**
 * A file visitor that handles errors based on an [ErrorHandler].
 *
 * @property [onError] A function that determines how errors are handled.
 */
private open class ErrorHandlingVisitor(val onError: ErrorHandler) : SimpleFileVisitor<Path>() {
    override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = onError(file, exc).visitResult
}

/**
 * Calls [tryFunc] and passes any thrown [IOException] with [file] to [onError].
 *
 * @return How the file walk should be handled.
 */
private fun handleWalkErrors(onError: ErrorHandler, file: Path, tryFunc: () -> Unit): FileVisitResult {
    try {
        tryFunc()
    } catch (e: IOException) {
        return onError(file, e).visitResult
    }

    return FileVisitResult.CONTINUE
}

/**
 * Recursively moves a file or directory from [source] to [target].
 *
 * If [source] and [target] are the same file, then nothing is moved.
 *
 * If the file to be moved is a symbolic link then the link itself, and not its target, is moved.
 *
 * Moving a file will copy its last modified time if supported by both file stores.
 *
 * This move may or may not be atomic. If it is not atomic and an exception is thrown, the state of the filesystem is
 * not defined.
 *
 * @property [source] The file or directory to move.
 * @property [target] The file or directory to move [source] to.
 * @property [overwrite] If a file or directory already exists at [target], replace it. If the directory is not empty,
 * it is deleted recursively.
 * @property [followLinks] Follow symbolic links when walking the directory tree.
 *
 * The following exceptions can be passed on [onError]:
 * - [NoSuchFileException]: There was an attempt to move a nonexistent file.
 * - [FileAlreadyExistsException]: The destination file already exists and [overwrite] is `false`.
 * - [AccessDeniedException]: There was an attempt to open a directory that didn't succeed.
 * - [FileSystemLoopException]: [followLinks] is `true` and a cycle of symbolic links was detected.
 * - [IOException]: Some other problem occurred while moving.
 */
fun moveRecursively(
    source: Path, target: Path,
    overwrite: Boolean = false,
    followLinks: Boolean = false,
    onError: ErrorHandler = DEFAULT_ERROR_HANDLER
) {
    val copyOptions = mutableSetOf<CopyOption>()
    if (overwrite) copyOptions.add(StandardCopyOption.REPLACE_EXISTING)

    val fileVisitor = object : ErrorHandlingVisitor(onError) {
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            val destFile = target.resolve(source.relativize(file))

            return handleWalkErrors(onError, destFile) {
                // [Files.move] will not replace a non-empty directory. You need to delete it recursively.
                if (overwrite) deleteRecursively(destFile, followLinks = followLinks, onError = onError)
                Files.move(file, destFile, *copyOptions.toTypedArray())
            }
        }

        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            val destDir = target.resolve(source.relativize(dir))

            // Attempt to move the whole directory in one operation. If this succeeds, there's no need to move its
            // descendants and you can skip the subtree. If this fails, copy the directory itself and continue walking
            // its descendants.
            return try {
                Files.move(dir, destDir, *copyOptions.toTypedArray())
                FileVisitResult.SKIP_SUBTREE
            } catch (e: IOException) {
                handleWalkErrors(onError, destDir) {
                    // [Files.copy] will not replace a non-empty directory. You need to delete it recursively.
                    if (overwrite) deleteRecursively(destDir, followLinks = followLinks, onError = onError)
                    Files.copy(dir, destDir, *copyOptions.toTypedArray())
                }
            }
        }

        override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
            val destDir = target.resolve(source.relativize(dir))

            // Adding files to the directory may change its attributes, so they need to be copied after it is visited.
            // The directory must be removed from [source] after all its contents have been moved.
            return handleWalkErrors(onError, destDir) {
                copyFileAttributes(dir, destDir)
                Files.deleteIfExists(dir)
                super.postVisitDirectory(dir, exc)
            }
        }
    }

    val walkOptions = mutableSetOf<FileVisitOption>()
    if (followLinks) walkOptions.add(FileVisitOption.FOLLOW_LINKS)

    Files.walkFileTree(source, walkOptions, Int.MAX_VALUE, fileVisitor)
}

/**
 * Recursively copies a file or directory from [source] to [target].
 *
 * If [source] and [target] are the same file, then nothing is copied.
 *
 * Copying a file or directory is not an atomic operation. If an [IOException] is thrown, then the state of the
 * filesystem is undefined.
 *
 * @property [source] The file or directory to copy.
 * @property [target] The file or directory to copy [source] to.
 * @property [overwrite] If a file or directory already exists at [target], replace it. If the directory is not empty,
 * it is deleted recursively.
 * @property [copyAttributes] Attempt to copy file attributes from [source] to [target]. The last modified time is
 * always copied if supported. Whether other attributes are copied is platform and filesystem dependent. File attributes
 * of links may not be copied.
 * @property [followLinks] Follow symbolic links when walking the directory tree and copy the targets of links instead
 * of links themselves.
 *
 * The following exceptions can be passed on [onError]:
 * - [NoSuchFileException]: There was an attempt to copy a nonexistent file.
 * - [FileAlreadyExistsException]: The destination file already exists.
 * - [AccessDeniedException]: There was an attempt to open a directory that didn't succeed.
 * - [FileSystemLoopException]: [followLinks] is `true` and a cycle of symbolic links was detected.
 * - [IOException]: Some other problem occurred while copying.
 */
fun copyRecursively(
    source: Path, target: Path,
    overwrite: Boolean = false,
    copyAttributes: Boolean = false,
    followLinks: Boolean = false,
    onError: ErrorHandler = DEFAULT_ERROR_HANDLER
) {
    val copyOptions = mutableSetOf<CopyOption>()
    if (overwrite) copyOptions.add(StandardCopyOption.REPLACE_EXISTING)
    if (copyAttributes) copyOptions.add(StandardCopyOption.COPY_ATTRIBUTES)
    if (!followLinks) copyOptions.add(LinkOption.NOFOLLOW_LINKS)

    val fileVisitor = object : ErrorHandlingVisitor(onError) {
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            val destFile = target.resolve(source.relativize(file))

            return handleWalkErrors(onError, destFile) {
                // [Files.copy] will not replace a non-empty directory. You need to delete it recursively.
                if (overwrite) deleteRecursively(destFile, followLinks = followLinks, onError = onError)
                Files.copy(file, destFile, *copyOptions.toTypedArray())
            }
        }

        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            val destDir = target.resolve(source.relativize(dir))

            // Copy the directory itself with its attributes if necessary.
            return handleWalkErrors(onError, destDir) {
                // [Files.copy] will not replace a non-empty directory. You need to delete it recursively.
                if (overwrite) deleteRecursively(destDir, followLinks = followLinks, onError = onError)
                Files.copy(dir, destDir, *copyOptions.toTypedArray())
            }
        }

        override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
            val destDir = target.resolve(source.relativize(dir))

            // Adding files to the directory may change its attributes, so they need to be copied after it is visited.
            return handleWalkErrors(onError, destDir) {
                if (copyAttributes) copyFileAttributes(dir, destDir)
                super.postVisitDirectory(dir, exc)
            }
        }
    }

    val walkOptions = mutableSetOf<FileVisitOption>()
    if (followLinks) walkOptions.add(FileVisitOption.FOLLOW_LINKS)

    Files.walkFileTree(source, walkOptions, Int.MAX_VALUE, fileVisitor)
}

/**
 * Creates a new file with given [attributes] and [contents].
 *
 * The file [attributes] are set atomically when the file is created. If more than one attribute of the same name is
 * passed in then all but the last occurrence is ignored.
 *
 * The creation of the file and the writing of [contents] to the file are not atomic.
 *
 * @property [path] The path of the new file.
 * @property [attributes] A set of file attributes to set atomically when creating the file.
 * @property [contents] A stream containing the data to fill the file with.
 *
 * The following exceptions can be passed on [onError]:
 * - [UnsupportedOperationException]: [attributes] contains an attribute that cannot be set atomically when creating
 *   the file.
 * - [FileAlreadyExistsException]: The file at [path] already exists.
 * - [IOException]: Some other problem occurred while creating the file.
 */
fun createFile(
    path: Path,
    attributes: Set<FileAttribute<*>> = emptySet(),
    contents: InputStream = ByteArrayInputStream(ByteArray(0)),
    onError: ErrorHandler = DEFAULT_ERROR_HANDLER
) {
    handleWalkErrors(onError, path) {
        Files.createFile(path, *attributes.toTypedArray())
        Files.copy(contents, path, StandardCopyOption.REPLACE_EXISTING)
    }
}

/**
 * Creates a new directory and any necessary parent directories with given [attributes].
 *
 * The file [attributes] are set atomically when the directories are created. If more than one attribute of the same
 * name is passed in then all but the last occurrence is ignored.
 *
 * This function does not throw if  * This operation is not atomic. Individual deletions may not be atomic either.[path] already exists and is a directory.
 *
 * If this method fails, then it may do so without having created all the directories.
 *
 * @property [path] The path of the new directory.
 * @property [attributes] A set of file attributes to set atomically when creating the directory.
 *
 * The following exceptions can be passed on [onError]:
 * - [UnsupportedOperationException]: [attributes] contains an attribute that cannot be set atomically when creating
 *   the directory.
 * - [FileAlreadyExistsException]: The file already exists but is not a directory.
 * - [IOException]: Some other problem occurred while creating the directory.
 */
fun createDir(
    path: Path,
    attributes: Set<FileAttribute<*>> = emptySet(),
    onError: ErrorHandler = DEFAULT_ERROR_HANDLER
) {
    handleWalkErrors(onError, path) {
        Files.createDirectories(path, *attributes.toTypedArray())
    }
}

/**
 * Recursively deletes a file or directory.
 *
 * This operation is not atomic. Deleting an individual file or directory may not be atomic either.
 *
 * If the file to be deleted is a symbolic link then the link itself, and not its target, is deleted.
 *
 * @property [path] The path of the file or directory to delete.
 * @property [followLinks] Follow symbolic links when walking the directory tree.
 *
 * The following exceptions can be passed on [onError]:
 * - [NoSuchFileException]: There was an attempt to delete a nonexistent file.
 * - [AccessDeniedException]: There was an attempt to open a directory that didn't succeed.
 * - [FileSystemLoopException]: [followLinks] is `true` and a cycle of symbolic links was detected.
 * - [IOException]: Some other problem occurred while deleting.
 */
fun deleteRecursively(
    path: Path,
    followLinks: Boolean = false,
    onError: ErrorHandler = DEFAULT_ERROR_HANDLER
) {
    val fileVisitor = object : ErrorHandlingVisitor(onError) {
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult =
            handleWalkErrors(onError, file) { Files.delete(file) }

        override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult =
        // The directory cannot be deleted until all its contents have been deleted.
            handleWalkErrors(onError, dir) {
                Files.delete(dir)
                super.postVisitDirectory(dir, exc)
            }
    }

    val options = mutableSetOf<FileVisitOption>()
    if (followLinks) options.add(FileVisitOption.FOLLOW_LINKS)

    Files.walkFileTree(path, options, Int.MAX_VALUE, fileVisitor)
}
