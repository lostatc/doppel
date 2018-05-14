package diffir

import java.nio.file.Path
import java.nio.file.Paths
import java.io.File

/**
 * The path of a file or directory.
 */
interface FSPath {
    /**
     * The segments of the path relative to [parent].
     *
     * These segments exclude path separators.
     */
    val relativeSegments: Array<out String>

    /**
     * The parent path. Null if there is no parent.
     *
     * @throws [IsAbsolutePathException] This exception is thrown if the property is set to a non-null value while
     * [pathSegments] is absolute.
     */
    val parent: FSPath?

    /**
     * The segments of the path.
     *
     * If [parent] is not null, then this is equal to [parent::pathSegments] + [relativeSegments].
     */
    val pathSegments: List<String>
        get() = (parent?.pathSegments ?: listOf<String>()) + relativeSegments

    /**
     * Returns the string representation of this path.
     */
    override fun toString(): String

    /**
     * Indicates wither the object [other] is equal to this one.
     *
     * This path and [other] are equal if they are the same type and if their [pathSegments] properties are equal.
     */
    override operator fun equals(other: Any?): Boolean

    /**
     * Return a hash code value for the object.
     */
    override fun hashCode(): Int

    /**
     * Returns whether the file represented by this path exists in the filesystem.
     *
     * @param checkType: Check not only whether the file exists, but also whether the type of file matches the type of
     * the object.
     */
    fun exists(checkType: Boolean = true): Boolean

    /**
     * Return a copy of this path.
     */
    fun copy(): MutableFSPath

    /**
     * Return a copy of this path which is relative to [ancestor].
     *
     * This method climbs the tree of parents until it finds the path whose parent is [ancestor]. It then sets that
     * path's parent to null.
     */
    fun relativeTo(ancestor: DirPathBase): FSPath

    /**
     * Returns a [Path] representing this path.
     */
    fun toPath(): Path = Paths.get("", *pathSegments.toTypedArray())

    /**
     * Returns a [File] representing this path.
     */
    fun toFile(): File = toPath().toFile()
}

/**
 * The path of a file.
 */
interface FilePathBase : FSPath {

}

/**
 * The path of a directory.
 */
interface DirPathBase : FSPath {
    /**
     * The paths of the immediate children of the directory.
     */
    val children: Set<MutableFSPath>

    /**
     * The paths of all descendants of the directory.
     */
    val descendants: Set<MutableFSPath>
        get() = walkChildren().toSet()

    /**
     * Return a copy of [other] with this as the ancestor.
     *
     * This method climbs the tree of parents until it finds a path whose parent is null. It then makes this that path's
     * parent.
     *
     * @throws [IsAbsolutePathException] This exception is thrown if [other] is an absolute path.
     */
    operator fun plus(other: FSPath): MutableFSPath

    /**
     * Return a sequence of all the descendants of this directory path.
     *
     * A top-down, depth-first search is used and directory paths are visited before their contents.
     */
    fun walkChildren(): Sequence<MutableFSPath>

    /**
     * Returns whether every path in the tree exists in the filesystem.
     */
    fun treeExists(checkType: Boolean = true): Boolean = exists(checkType) && walkChildren().all { it.exists(checkType) }

    /**
     * Return a representation of the difference between two directories.
     */
    infix fun diff(other: DirPathBase) = PathDiff(this, other)
}
