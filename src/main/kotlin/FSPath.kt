package diffir

import java.io.IOException
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

/**
 * A read-only representation of a file or directory path.
 *
 * Properties in this interface must be read-only and methods must not modify the state. [FSPath] objects can represent
 * absolute or relative paths. Unlike [java.nio.file.Path], objects of this type form a tree to represent a file
 * hierarchy; every path has a [parent] property that points to a separate [FSPath] object. Each [FSPath] object stores
 * only a file name; the full path is retrieved by joining the paths of all of its ancestors.
 */
interface FSPath {
    /**
     * The name of the current file.
     */
    val fileName: String

    /**
     * The parent path. Null if there is no parent.
     */
    val parent: DirPath?

    /**
     * The segments of the path, excluding path separators.
     *
     * If [parent] is `null`, then this is equal to [fileName]. Otherwise it is equal to
     * [parent.pathSegments][DirPath.pathSegments] + [fileName].
     */
    val pathSegments: List<String>
        get() = (parent?.pathSegments ?: listOf()) + fileName

    /**
     * Whether the path is an absolute path.
     */
    val isAbsolute: Boolean
        get() = toPath().isAbsolute

    /**
     * Returns the string representation of this path.
     */
    override fun toString(): String

    /**
     * Indicates wither the object [other] is equal to this one.
     *
     * This path and [other] are equal if they are the same type and their [fileName] and [parent] properties are equal.
     */
    override operator fun equals(other: Any?): Boolean

    /**
     * Returns a hash code value for the object.
     *
     * The hash code value is based on the [fileName] and [parent] properties.
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
    fun copy(fileName: String = this.fileName, parent: DirPath? = this.parent): FSPath

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
     * Returns a [Path] representing this path.
     *
     * @param [filesystem] The filesystem to use to construct the path. By default, this uses the default filesystem.
     */
    fun toPath(filesystem: FileSystem = FileSystems.getDefault()): Path =
        filesystem.getPath(pathSegments.first(), *pathSegments.drop(1).toTypedArray())

    /**
     * Returns whether this path starts with the path [other].
     *
     * @see [Path.startsWith]
     */
    fun startsWith(other: FSPath): Boolean = toPath().startsWith(other.toPath())

    /**
     * Returns whether this path ends with the path [other].
     *
     * @see [Path.endsWith]
     */
    fun endsWith(other: FSPath): Boolean = toPath().endsWith(other.toPath())
}

/**
 * A read-only representation of a file path.
 */
interface FilePath : FSPath {
    override fun copy(fileName: String, parent: DirPath?): FilePath

    override fun relativeTo(ancestor: DirPath): FilePath

    override fun withAncestor(ancestor: DirPath): FilePath

    /**
     * Return a copy of this path as a mutable file path.
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

    override fun copy(fileName: String, parent: DirPath?): DirPath

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
                else -> compareContents(it.withAncestor(this).toPath(), it.withAncestor(other).toPath())
            }
        }.toSet()
        val different = common - same

        // Compare the times of files in the directories.
        val rightNewer = common.asSequence().filter {
            val leftTime = Files.getLastModifiedTime(it.withAncestor(this).toPath())
            val rightTime = Files.getLastModifiedTime(it.withAncestor(other).toPath())
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
     * Return a copy of this path as a mutable directory path.
     */
    fun toMutableDirPath(): MutableDirPath = copy() as MutableDirPath
}
