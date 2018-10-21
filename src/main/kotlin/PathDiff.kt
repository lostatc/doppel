package diffir

import java.nio.file.Files
import java.nio.file.Path

/**
 * This function returns true if the two files are the same and false otherwise.
 */
typealias FileCompareFunc = (Path, Path) -> Boolean

/**
 * This function compares the files [left] and [right] by size and checksum and returns whether they are the same.
 *
 * Checksums are only compared when both files are the same size.
 */
private fun compareByHash(left: Path, right: Path): Boolean {
    if (Files.size(left) != Files.size(right)) return false
    return getFileChecksum(left) contentEquals getFileChecksum(right)
}

/**
 * A comparison of two directories.
 *
 * @property [left] The first directory to compare.
 * @property [right] The second directory to compare.
 * @property [fileCompareFunc] The function used to determine if two files are the same. The default function compares
 * files by size and checksum.
 */
class PathDiff(
    val left: DirPath,
    val right: DirPath,
    val fileCompareFunc: FileCompareFunc = ::compareByHash
) {
    /**
     * The descendants of the left directory as relative paths.
     */
    private val leftRelativeDescendants
        get() = left.descendants.asSequence().map { it.relativeTo(left) }.toSet()

    /**
     * The descendants of the right directory as relative paths.
     */
    private val rightRelativeDescendants
        get() = right.descendants.asSequence().map { it.relativeTo(right) }.toSet()

    /**
     * The paths that exist in both directory trees.
     *
     * This returns relative paths.
     */
    val common: Set<FSPath>
        get() = leftRelativeDescendants intersect rightRelativeDescendants

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
     * The paths of files and directories that are the same in both directory trees.
     *
     * Files are the same if they have the same relative path and the same contents according to [fileCompareFunc].
     *
     * Directories are the same if they have the same relative path and the same [children][DirPath.children].
     *
     * This returns relative paths.
     */
    val same: Set<FSPath>
        get() = common.asSequence().filter {
            when (it) {
                is DirPath -> it.withAncestor(left).children == it.withAncestor(right).children
                else -> fileCompareFunc(it.withAncestor(left).toPath(), it.withAncestor(right).toPath())
            }
        }.toSet()

    /**
     * The paths of files and directories that are different in both directory trees.
     *
     * Files are different if they have the same relative path and different contents according to [fileCompareFunc].
     *
     * Directories are different if they have the same relative path and different [children][DirPath.children].
     *
     * This returns relative paths.
     */
    val different: Set<FSPath>
        get() = common - same

    /**
     * The paths of files and directories that were modified more recently in the right tree than in the left tree.
     *
     * This returns relative paths.
     */
    val rightNewer: Set<FSPath>
        get() = common.asSequence().filter {
            val leftTime = Files.getLastModifiedTime(it.withAncestor(left).toPath())
            val rightTime = Files.getLastModifiedTime(it.withAncestor(right).toPath())
            rightTime > leftTime
        }.toSet()

    /**
     * The paths of files and directories that were modified more recently in the left tree than in the right tree.
     *
     * This returns relative paths.
     */
    val leftNewer: Set<FSPath>
        get() = common - rightNewer
}