package diffir

import java.nio.file.Path
import java.nio.file.Paths
import java.io.File

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
     *
     * @throws [IsAbsolutePathException] This exception is thrown if the property is set to a non-null value while
     * [fileName] is a filesystem root.
     */
    val parent: DirPath?

    /**
     * The segments of the path, excluding path separators.
     *
     * If [parent] is `null`, then this is equal to [fileName]. Otherwise it is equal to
     * [parent.pathSegments][DirPath.pathSegments] + [fileName].
     */
    val pathSegments: List<String>
        get() = (parent?.pathSegments ?: listOf<String>()) + fileName

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
     * Returns a hash code value for the object.
     *
     * The hash code value is based on the [pathSegments] property.
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
     */
    fun copy(): MutableFSPath

    /**
     * Returns a copy of this path which is relative to [ancestor].
     *
     * This method climbs the tree of parents until it finds the path whose parent is [ancestor]. It then sets that
     * path's parent to `null`. If this is an absolute path, then [ancestor] must be an an absolute path. Similarly, if
     * this is a relative path, then [ancestor] must also be a relative path.
     *
     * @throws [IllegalArgumentException] This exception is thrown if [ancestor] is not an ancestor of this path.
     */
    fun relativeTo(ancestor: DirPathBase): MutableFSPath

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
 * A read-only representation of a file path.
 */
interface FilePathBase : FSPath {
    override fun copy(): FilePath
}

/**
 * A read-only representation of a directory path.
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

    override fun copy(): DirPath

    /**
     * Returns a copy of [other] with this as the ancestor.
     *
     * This method climbs the tree of parents until it finds a path whose parent is `null`. It then makes this that path's
     * parent.
     *
     * @throws [IsAbsolutePathException] This exception is thrown if [other] is an absolute path.
     */
    operator fun plus(other: FSPath): MutableFSPath

    /**
     * Returns a sequence of all the descendants of this directory path.
     *
     * A top-down, depth-first search is used and directory paths are visited before their contents.
     */
    fun walkChildren(): Sequence<MutableFSPath>

    /**
     * Returns whether every path in the tree exists in the filesystem.
     *
     * @param [checkType] Check not only whether each file exists, but also whether the type of file matches the type of
     * the object.
     */
    fun treeExists(checkType: Boolean = true): Boolean = exists(checkType) && walkChildren().all { it.exists(checkType) }

    /**
     * Returns a representation of the difference between two directories.
     */
    infix fun diff(other: DirPathBase) = PathDiff(this, other)
}
