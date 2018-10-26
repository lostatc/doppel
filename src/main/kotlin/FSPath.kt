package diffir

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * A read-only representation of a file or directory path.
 *
 * [FSPath] objects wrap a [Path] object to allow them to form a tree of file and directory paths. This allows file
 * hierarchies to be represented and manipulated in memory. Every [FSPath] object has a [fileName] property representing
 * the name of the current file and a [parent] property that points to another [FSPath] object. A [Path] object
 * representing the path can be accessed through the [path] property.
 */
interface FSPath {
    /**
     * The name of the file or directory represented by this path.
     */
    val fileName: Path

    /**
     * The parent path or `null` if there is no parent.
     */
    val parent: DirPath?

    /**
     * A [Path] representing this path.
     *
     * This is computed using on [fileName] and [parent].
     */
    val path: Path

    /**
     * Returns the string representation of this path.
     */
    override fun toString(): String

    /**
     * Indicates wither the object [other] is equal to this one.
     *
     * This path and [other] are equal if they are the same type and their [path] and [parent] properties are equal.
     */
    override operator fun equals(other: Any?): Boolean

    /**
     * Returns a hash code value for the object.
     *
     * The hash code value is based on the [path] and [parent] properties.
     */
    override fun hashCode(): Int

    /**
     * Returns whether the file represented by this path exists in the filesystem.
     *
     * @param [checkType] Check not only whether the file exists, but also whether the type of file matches the type of
     * the the object.
     */
    fun exists(checkType: Boolean = true): Boolean

    /**
     * Returns a copy of this path.
     *
     * @param [fileName] The new file name to assign to the copy.
     * @param [parent] The new parent to assign to the copy.
     */
    fun copy(fileName: Path = this.fileName, parent: DirPath? = this.parent): FSPath

    /**
     * Returns a copy of this path which is relative to [ancestor].
     *
     * This method climbs the tree of parents until it finds the path whose parent is [ancestor]. It then returns a copy
     * of that path with the parent set to `null`. If this is an absolute path, then [ancestor] must be an an absolute
     * path. Similarly, if this is a relative path, then [ancestor] must also be a relative path.
     *
     * @throws [IllegalArgumentException] This exception is thrown if [ancestor] is not an ancestor of this path.
     */
    fun relativeTo(ancestor: DirPath): FSPath

    /**
     * Returns a copy of this path with [ancestor] as its ancestor.
     *
     * This method climbs the tree of parents until it finds a path whose parent is `null`. It then makes that path's
     * parent a copy of [ancestor]. If this path is absolute, then this method returns a copy of this path.
     */
    fun withAncestor(ancestor: DirPath): FSPath

    /**
     * Returns whether this path starts with the path [other].
     *
     * @see [Path.startsWith]
     */
    fun startsWith(other: FSPath): Boolean = path.startsWith(other.path)

    /**
     * Returns whether this path ends with the path [other].
     *
     * @see [Path.endsWith]
     */
    fun endsWith(other: FSPath): Boolean = path.endsWith(other.path)
}

/**
 * A read-only representation of a file path.
 */
interface FilePath : FSPath {
    override fun copy(fileName: Path, parent: DirPath?): FilePath

    override fun relativeTo(ancestor: DirPath): FilePath

    override fun withAncestor(ancestor: DirPath): FilePath

    /**
     * Returns a copy of this path as a mutable file path.
     */
    fun toMutableFilePath(): MutableFilePath = copy() as MutableFilePath
}

/**
 * A read-only representation of a directory path.
 */
interface DirPath : FSPath {
    /**
     * The paths of the immediate children of the directory.
     */
    val children: Set<FSPath>

    /**
     * The paths of all the descendants of the directory.
     */
    val descendants: Set<FSPath>

    override fun copy(fileName: Path, parent: DirPath?): DirPath

    override fun relativeTo(ancestor: DirPath): DirPath

    override fun withAncestor(ancestor: DirPath): DirPath

    /**
     * Returns a sequence of all the descendants of this directory path.
     *
     * This walks through the tree of [children]. A top-down, depth-first search is used and directory paths are visited
     * before their contents.
     */
    fun walkChildren(): Sequence<FSPath> {
        fun walk(node: DirPath): Sequence<FSPath> {
            return node.children.asSequence().flatMap {
                if (it is DirPath) sequenceOf(it) + walk(it) else sequenceOf(it)
            }
        }
        return walk(this)
    }

    /**
     * Returns whether every path in the tree exists in the filesystem.
     *
     * @param [checkType] Check not only whether each file exists, but also whether the type of file matches the type of
     * the object.
     */
    fun treeExists(checkType: Boolean = true): Boolean =
        exists(checkType) && walkChildren().all { it.exists(checkType) }

    /**
     * Returns an immutable representation of the difference between two directories.
     *
     * @throws [IOException] An I/O error occurred.
     */
    infix fun diff(other: DirPath): PathDiff {
        // Get the descendants of the directories as relative paths.
        val leftRelativeDescendants = this.descendants.asSequence().map { it.relativeTo(this) }.toSet()
        val rightRelativeDescendants = other.descendants.asSequence().map { it.relativeTo(other) }.toSet()

        // Compare files in the directories.
        val common = leftRelativeDescendants intersect rightRelativeDescendants
        val leftOnly = leftRelativeDescendants - rightRelativeDescendants
        val rightOnly = rightRelativeDescendants - leftRelativeDescendants

        // Compare the contents of files in the directories.
        val same = common.asSequence().filter {
            when (it) {
                is DirPath -> it.withAncestor(this).children == it.withAncestor(other).children
                else -> compareContents(it.withAncestor(this).path, it.withAncestor(other).path)
            }
        }.toSet()
        val different = common - same

        // Compare the times of files in the directories.
        val rightNewer = common.asSequence().filter {
            val leftTime = Files.getLastModifiedTime(it.withAncestor(this).path)
            val rightTime = Files.getLastModifiedTime(it.withAncestor(other).path)
            rightTime > leftTime
        }.toSet()
        val leftNewer = common - rightNewer

        return PathDiff(
            this, other,
            common = common,
            leftOnly = leftOnly, rightOnly = rightOnly,
            same = same, different = different,
            leftNewer = leftNewer, rightNewer = rightNewer
        )
    }

    /**
     * Returns a copy of this path as a mutable directory path.
     */
    fun toMutableDirPath(): MutableDirPath = copy() as MutableDirPath
}
