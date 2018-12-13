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

package io.github.lostatc.doppel.filesystem

import io.github.lostatc.doppel.handlers.ErrorHandler
import io.github.lostatc.doppel.handlers.PathConverter
import java.io.IOException
import java.nio.file.CopyOption
import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitor
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.DigestInputStream
import java.security.MessageDigest

/**
 * An algorithm used to create a message digest.
 *
 * @property [algorithmName] The name of the algorithm.
 */
enum class DigestAlgorithm(val algorithmName: String) {
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
 * This function computes and returns a checksum of the given [file] using the given [algorithm].
 *
 * @param [bufferSize] The file is read this many bytes at a time.
 *
 * @throws [IOException] An I/O error occurred.
 */
fun getFileChecksum(
    file: Path,
    algorithm: DigestAlgorithm = DigestAlgorithm.SHA1,
    bufferSize: Int = 4096
): ByteArray {
    val messageDigest = MessageDigest.getInstance(algorithm.algorithmName)
    val inputStream = Files.newInputStream(file)
    val buffer = ByteArray(bufferSize)

    DigestInputStream(inputStream, messageDigest).use {
        do {
            val bytesRead = it.read(buffer)
        } while (bytesRead != -1)
    }

    return messageDigest.digest()
}

/**
 * Copies basic file attributes from [source] to [target].
 */
internal fun copyFileAttributes(source: Path, target: Path) {
    val mtime = Files.getLastModifiedTime(source)
    Files.setLastModifiedTime(target, mtime)
}

/**
 * A file visitor that handles errors using an [ErrorHandler].
 */
private interface ErrorHandlingVisitor : FileVisitor<Path> {
    /**
     * A function that determines how errors are handled.
     */
    val errorHandler: ErrorHandler

    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = FileVisitResult.CONTINUE

    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = FileVisitResult.CONTINUE

    override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult =
        errorHandler.handle(file, exc).visitResult

    override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult =
        if (exc == null) FileVisitResult.CONTINUE else errorHandler.handle(dir, exc).visitResult
}

/**
 * Calls [tryFunc] and passes any thrown [IOException] with [file] to [errorHandler].
 *
 * @return How the file walk should be handled.
 */
private fun handleWalkErrors(errorHandler: ErrorHandler, file: Path, tryFunc: () -> Unit): FileVisitResult {
    return try {
        tryFunc()
        FileVisitResult.CONTINUE
    } catch (e: IOException) {
        errorHandler.handle(file, e).visitResult
    }
}

/**
 * Recursively moves a file or directory from [source] to [target].
 *
 * @see [MoveAction]
 */
internal fun moveRecursively(
    source: Path, target: Path,
    overwrite: Boolean, atomic: Boolean, followLinks: Boolean,
    pathConverter: PathConverter,
    errorHandler: ErrorHandler
) {
    val moveOptions = listOfNotNull(
        if (overwrite) StandardCopyOption.REPLACE_EXISTING else null,
        if (atomic) StandardCopyOption.ATOMIC_MOVE else null
    ).toTypedArray()

    val copyOptions = listOfNotNull(
        if (overwrite) StandardCopyOption.REPLACE_EXISTING else null
    ).toTypedArray()

    val fileVisitor = object : ErrorHandlingVisitor {
        override val errorHandler = errorHandler

        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            val destFile = try {
                target.resolve(pathConverter.convert(source.relativize(file), target.fileSystem))
            } catch (e: InvalidPathException) {
                return errorHandler.handle(file, e).visitResult
            }

            return handleWalkErrors(errorHandler, file) {
                // [Files.move] will not replace a non-empty directory. You need to delete it recursively.
                if (overwrite) deleteRecursively(destFile, followLinks = followLinks, errorHandler = errorHandler)
                Files.move(file, destFile, *moveOptions)
            }
        }

        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            val destDir = try {
                target.resolve(pathConverter.convert(source.relativize(dir), target.fileSystem))
            } catch (e: InvalidPathException) {
                return errorHandler.handle(dir, e).visitResult
            }

            // Attempt to move the whole directory in one operation. If this succeeds, there's no need to move its
            // descendants and you can skip the subtree. If this fails, copy the directory itself and continue walking
            // its descendants.
            return try {
                Files.move(dir, destDir, *moveOptions)
                FileVisitResult.SKIP_SUBTREE
            } catch (e: IOException) {
                // An atomic move didn't happen.
                if (atomic) return errorHandler.handle(dir, e).visitResult

                handleWalkErrors(errorHandler, dir) {
                    // [Files.copy] will not replace a non-empty directory. You need to delete it recursively.
                    if (overwrite) deleteRecursively(destDir, followLinks = followLinks, errorHandler = errorHandler)
                    Files.copy(dir, destDir, *copyOptions)
                }
            }
        }

        override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
            val destDir = try {
                target.resolve(pathConverter.convert(source.relativize(dir), target.fileSystem))
            } catch (e: InvalidPathException) {
                return errorHandler.handle(dir, e).visitResult
            }

            // Adding files to the directory may change its attributes, so they need to be copied after it is visited.
            // The directory must be removed from [source] after all its contents have been moved.
            return handleWalkErrors(errorHandler, dir) {
                copyFileAttributes(dir, destDir)
                Files.deleteIfExists(dir)
                super.postVisitDirectory(dir, exc)
            }
        }
    }

    val walkOptions = listOfNotNull(
        if (followLinks) FileVisitOption.FOLLOW_LINKS else null
    ).toSet()

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
    pathConverter: PathConverter,
    errorHandler: ErrorHandler
) {
    val copyOptions = listOfNotNull<CopyOption>(
        if (overwrite) StandardCopyOption.REPLACE_EXISTING else null,
        if (copyAttributes) StandardCopyOption.COPY_ATTRIBUTES else null,
        if (!followLinks) LinkOption.NOFOLLOW_LINKS else null
    ).toTypedArray()

    val fileVisitor = object : ErrorHandlingVisitor {
        override val errorHandler = errorHandler

        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            val destFile = try {
                target.resolve(pathConverter.convert(source.relativize(file), target.fileSystem))
            } catch (e: InvalidPathException) {
                return errorHandler.handle(file, e).visitResult
            }

            return handleWalkErrors(errorHandler, file) {
                // [Files.copy] will not replace a non-empty directory. You need to delete it recursively.
                if (overwrite) deleteRecursively(destFile, followLinks = followLinks, errorHandler = errorHandler)
                Files.copy(file, destFile, *copyOptions)
            }
        }

        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            val destDir = try {
                target.resolve(pathConverter.convert(source.relativize(dir), target.fileSystem))
            } catch (e: InvalidPathException) {
                return errorHandler.handle(dir, e).visitResult
            }

            // Copy the directory itself with its attributes if necessary.
            return handleWalkErrors(errorHandler, dir) {
                // [Files.copy] will not replace a non-empty directory. You need to delete it recursively.
                if (overwrite) deleteRecursively(destDir, followLinks = followLinks, errorHandler = errorHandler)
                Files.copy(dir, destDir, *copyOptions)
            }
        }

        override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
            val destDir = try {
                target.resolve(pathConverter.convert(source.relativize(dir), target.fileSystem))
            } catch (e: InvalidPathException) {
                return errorHandler.handle(dir, e).visitResult
            }

            // Adding files to the directory may change its attributes, so they need to be copied after it is visited.
            return handleWalkErrors(errorHandler, dir) {
                if (copyAttributes) copyFileAttributes(dir, destDir)
                super.postVisitDirectory(dir, exc)
            }
        }
    }

    val walkOptions = listOfNotNull(
        if (followLinks) FileVisitOption.FOLLOW_LINKS else null
    ).toSet()

    Files.walkFileTree(source, walkOptions, Int.MAX_VALUE, fileVisitor)
}

/**
 * Recursively deletes a file or directory at [path].
 *
 * @see [DeleteAction]
 */
internal fun deleteRecursively(path: Path, followLinks: Boolean, errorHandler: ErrorHandler) {
    val fileVisitor = object : ErrorHandlingVisitor {
        override val errorHandler = errorHandler

        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult =
            handleWalkErrors(errorHandler, file) { Files.delete(file) }

        override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult =
        // The directory cannot be deleted until all its contents have been deleted.
            handleWalkErrors(errorHandler, dir) {
                Files.delete(dir)
                super.postVisitDirectory(dir, exc)
            }
    }

    val walkOptions = listOfNotNull(
        if (followLinks) FileVisitOption.FOLLOW_LINKS else null
    ).toSet()

    Files.walkFileTree(path, walkOptions, Int.MAX_VALUE, fileVisitor)
}
