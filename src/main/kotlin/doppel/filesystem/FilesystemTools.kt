/*
 * Copyright © 2018 Garrett Powell <garrett@gpowell.net>
 *
 * This file is part of doppel.
 *
 * doppel is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * doppel is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with doppel.  If not, see <https://www.gnu.org/licenses/>.
 */

package doppel.filesystem

import doppel.error.ErrorHandler
import java.io.IOException
import java.nio.file.CopyOption
import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
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
 * An algorithm used to create a message digest.
 *
 * @property [algorithmName] The name of the algorithm.
 */
internal enum class DigestAlgorithm(val algorithmName: String) {
    /**
     * The MD5 message digest algorithm.
     */
    MD5("MD5"),

    /**
     * The SHA-1 hash algorithm.
     */
    SHA1("SHA-1"),

    /**
     * The SHA-256 hash algorithm.
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
internal fun getFileChecksum(file: Path, algorithm: DigestAlgorithm = DigestAlgorithm.SHA1): ByteArray {
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
 * @see [MoveAction]
 */
internal fun moveRecursively(
    source: Path, target: Path,
    overwrite: Boolean, followLinks: Boolean,
    onError: ErrorHandler
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
 * @see [CopyAction]
 */
internal fun copyRecursively(
    source: Path, target: Path,
    overwrite: Boolean, copyAttributes: Boolean, followLinks: Boolean,
    onError: ErrorHandler
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
 * Recursively deletes a file or directory at [path].
 *
 * @see [DeleteAction]
 */
internal fun deleteRecursively(
    path: Path,
    followLinks: Boolean,
    onError: ErrorHandler
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
