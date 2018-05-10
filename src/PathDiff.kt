package diffir

import java.io.File
import java.security.MessageDigest
import java.security.DigestInputStream

/**
 * This function returns true if the two files are the same and false otherwise.
 */
typealias FileCompareFunc = (File, File) -> Boolean

/**
 * This is the size of the buffer used when computing the checksum of a file.
 */
const val CHECKSUM_BUFFER_SIZE = 4096

/**
 * This function computes and returns a SHA-256 checksum of the given [file].
 */
internal fun getFileChecksum(file: File): ByteArray {
    val messageDigest = MessageDigest.getInstance("SHA-256")
    val inputStream = file.inputStream()
    val buffer = ByteArray(CHECKSUM_BUFFER_SIZE)

    DigestInputStream(inputStream, messageDigest).use {
        do {
            val bytesRead = it.read(buffer)
        } while (bytesRead != -1)
    }

    return messageDigest.digest()
}

/**
 * This function compares the files [left] and [right] by size and checksum and returns whether they are the same.
 *
 * Checksums are only created for files which are the same size.
 */
fun compareHash(left: File, right: File): Boolean {
    if (left.length() != right.length()) return false
    return getFileChecksum(left) contentEquals getFileChecksum(right)
}

/**
 * A comparison of two directories.
 *
 * @property [left] The first directory to compare.
 * @property [right] The second directory to compare.
 * @property [fileCompareFunc] The function used to determine if two files are the same.
 */
class PathDiff(
    val left: DirPath,
    val right: DirPath,
    val fileCompareFunc: FileCompareFunc = ::compareHash
) {
    /**
     * The descendants of the left directory as relative paths.
     */
    private val leftRelativeDescendants
        get() = left.descendants.map { it.relativeTo(left) }.toSet()

    /**
     * The descendants of the right directory as relative paths.
     */
    private val rightRelativeDescendants
        get() = right.descendants.map { it.relativeTo(right) }.toSet()

    /**
     * The paths that exist in both directory trees.
     *
     * This returns relative paths.
     */
    val common: Set<FSPath>
        get() = leftRelativeDescendants intersect rightRelativeDescendants

    /**
     * The paths of files that exist in both directory trees.
     *
     * This returns relative paths.
     */
    val commonFiles: Set<FilePath>
        get() = common.filterIsInstance<FilePath>().toSet()

    /**
     * The paths of directories that exist in both directory trees.
     *
     * This returns relative paths.
     */
    val commonDirs: Set<DirPath>
        get() = common.filterIsInstance<DirPath>().toSet()

    /**
     * The paths that exist in the left tree but not the right tree.
     *
     * This returns relative paths.
     */
    val leftOnly: Set<FSPath>
        get() = leftRelativeDescendants - rightRelativeDescendants

    /**
     * The paths that exist in the right tree but not the left tree.
     *
     * This returns relative paths.
     */
    val rightOnly: Set<FSPath>
        get() = rightRelativeDescendants - leftRelativeDescendants

    /**
     * The paths of files that are the same in both directory trees.
     *
     * These are files that have the same relative path in both directory trees and the same contents according to
     * [fileCompareFunc]. This returns relative paths.
     */
    val sameFiles: Set<FilePath>
        get() = commonFiles.filter { fileCompareFunc((left + it).toFile(), (right + it).toFile()) }.toSet()

    /**
     * The paths of files that are different in both directory trees.
     *
     * These are files that have the same relative path in both directory trees and different contents according to
     * [fileCompareFunc]. This returns relative paths.
     */
    val differentFiles: Set<FilePath>
        get() = commonFiles - sameFiles

    /**
     * The paths of files that were modified more recently in the right tree than in the left tree.
     *
     * This returns relative paths.
     */
    val rightNewer: Set<FSPath>
        get() = commonFiles.filter { (right + it).toFile().lastModified() > (left + it).toFile().lastModified() }.toSet()

    /**
     * The paths of files that were modified more recently in the left tree than in the right tree.
     *
     * This returns relative paths.
     */
    val leftNewer: Set<FSPath>
        get() = commonFiles - rightNewer
}