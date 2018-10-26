package diffir

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.security.DigestInputStream
import java.security.MessageDigest

/**
 * Copies basic file attributes from [source] to [target].
 */
internal fun copyFileAttributes(source: Path, target: Path) {
    val mtime = Files.getLastModifiedTime(source)
    Files.setLastModifiedTime(target, mtime)
}

/**
 * Compares the files [left] and [right] by size and checksum and returns whether they have the same contents.
 *
 * Checksums are only compared when both files are the same size.
 *
 * @throws [IOException] An I/O error occurred.
 */
internal fun compareContents(left: Path, right: Path): Boolean {
    if (Files.size(left) != Files.size(right)) return false
    return getFileChecksum(left) contentEquals getFileChecksum(right)
}

/**
 * An algorithm used to create a message digest.
 *
 * @param [algorithmName] The name of the algorithm.
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
 *
 * @throws [IOException] An I/O error occurred.
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
 * @param [source] The file or directory to move.
 * @param [target] The file or directory to move [source] to.
 * @param [overwrite] If a file or directory already exists at [target], replace it. If the directory is not empty, it
 * is deleted recursively.
 * @param [followLinks] Follow symbolic links when walking the directory tree.
 * @param [onError] A function that handles errors.
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

            return handleWalkErrors(onError, file) {
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
                handleWalkErrors(onError, dir) {
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
            return handleWalkErrors(onError, dir) {
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
 * @param [source] The file or directory to copy.
 * @param [target] The file or directory to copy [source] to.
 * @param [overwrite] If a file or directory already exists at [target], replace it. If the directory is not empty, it
 * is deleted recursively.
 * @param [copyAttributes] Attempt to copy file attributes from [source] to [target]. The last modified time is always
 * copied if supported. Whether other attributes are copied is platform and filesystem dependent. File attributes of
 * links may not be copied.
 * @param [followLinks] Follow symbolic links when walking the directory tree and copy the targets of links instead of
 * links themselves.
 * @param [onError] A function that handles errors.
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

            return handleWalkErrors(onError, file) {
                // [Files.copy] will not replace a non-empty directory. You need to delete it recursively.
                if (overwrite) deleteRecursively(destFile, followLinks = followLinks, onError = onError)
                Files.copy(file, destFile, *copyOptions.toTypedArray())
            }
        }

        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            val destDir = target.resolve(source.relativize(dir))

            // Copy the directory itself with its attributes if necessary.
            return handleWalkErrors(onError, dir) {
                // [Files.copy] will not replace a non-empty directory. You need to delete it recursively.
                if (overwrite) deleteRecursively(destDir, followLinks = followLinks, onError = onError)
                Files.copy(dir, destDir, *copyOptions.toTypedArray())
            }
        }

        override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
            val destDir = target.resolve(source.relativize(dir))

            // Adding files to the directory may change its attributes, so they need to be copied after it is visited.
            return handleWalkErrors(onError, dir) {
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
 * Creates a new file at [path] with given [attributes] and [contents].
 *
 * The creation of the file and the writing of [contents] to the file are not atomic.
 *
 * @param [path] The path of the new file.
 * @param [attributes] A set of file attributes to set atomically when creating the file.
 * @param [contents] A stream containing the data to fill the file with.
 * @param [onError] A function that handles errors.
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
 * Creates a symbolic link named [link] pointing to [target].
 *
 * The [target] may be an absolute or relative path and may not exist. If [target] is relative, then it is considered
 * relative to [link].
 *
 * If the underlying [FileStore] does not support symbolic links or special privileges are required to create them, an
 * [IOException] is thrown.
 *
 * @param [link] The path of the symbolic link.
 * @param [target] The path the symbolic link points to.
 * @param [attributes] A set of file attributes to set atomically when creating the file.
 * @param [onError] A function that handles errors.
 *
 * The following exceptions can be passed on [onError]:
 * - [UnsupportedOperationException]: [attributes] contains an attribute that cannot be set atomically when creating
 *   the file.
 * - [FileAlreadyExistsException]: [link] already exists.
 * - [IOException]: Some other problem occurred while creating the link.
 */
fun createSymbolicLink(
    link: Path,
    target: Path,
    attributes: Set<FileAttribute<*>> = emptySet(),
    onError: ErrorHandler = DEFAULT_ERROR_HANDLER
) {
    handleWalkErrors(onError, link) {
        Files.createSymbolicLink(link, target, *attributes.toTypedArray())
    }
}

/**
 * Creates a new directory and [path] with any necessary parent directories and given [attributes].
 *
 * This function does not throw if [path] already exists and is a directory.
 *
 * This operation is not atomic. Individual deletions may not be atomic either.
 *
 * If this method fails, then it may do so without having created all the directories.
 *
 * @param [path] The path of the new directory.
 * @param [attributes] A set of file attributes to set atomically when creating the directory.
 * @param [onError] A function that handles errors.
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
 * Recursively deletes a file or directory at [path].
 *
 * This operation is not atomic. Deleting an individual file or directory may not be atomic either.
 *
 * If the file to be deleted is a symbolic link then the link itself, and not its target, is deleted.
 *
 * @param [path] The path of the file or directory to delete.
 * @param [followLinks] Follow symbolic links when walking the directory tree.
 * @param [onError] A function that handles errors.
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
