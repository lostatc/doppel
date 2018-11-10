package diffir

import java.io.IOException
import java.nio.file.Path

/**
 * A read-only representation of a file or directory tree.
 *
 * Objects of this type wrap a [Path] object to allow them to form a tree of file and directory paths. This allows file
 * hierarchies to be represented and manipulated in memory.
 *
 * This class works like a prefix tree, where each [PathNode] stores only a single path segment as [fileName]. The
 * parent path can be accessed through the [parent] property and a map of child paths can be accessed through the
 * [children] property. The full [Path] can be accessed through the [path] property.
 *
 * Each [PathNode] has a [type], which indicates the type of file the path represents in the filesystem.
 */
interface PathNode {
    /**
     * The name of the file or directory represented by this path.
     */
    val fileName: Path

    /**
     * The parent path or `null` if there is no parent.
     */
    val parent: PathNode?

    /**
     * The type of file represented by this path.
     */
    val type: FileType

    /**
     * The ancestor whose [parent] is `null`, which could be this path.
     */
    val root: PathNode

    /**
     * A [Path] representing this path.
     *
     * This is computed using [fileName] and [parent].
     */
    val path: Path

    /**
     * A map of file names to path nodes for the immediate children of this path.
     */
    val children: Map<Path, PathNode>

    /**
     * A map of file paths to path nodes for all the descendants of this path.
     */
    val descendants: Map<Path, PathNode>

    /**
     * Returns the string representation of this path.
     */
    override fun toString(): String

    /**
     * Indicates wither the object [other] is equal to this one.
     */
    override operator fun equals(other: Any?): Boolean

    /**
     * Returns a hash code value for the object.
     */
    override fun hashCode(): Int

    /**
     * Returns a copy of this object as a [PathNode] object.
     *
     * Children are copied deeply.
     */
    fun toPathNode(): PathNode

    /**
     * Returns a copy of this object as a [MutablePathNode] object.
     *
     * Children are copied deeply.
     */
    fun toMutablePathNode(): MutablePathNode

    /**
     * Returns a sequence of all the ancestors of this directory path.
     *
     * @param [direction] The direction in which to iterate over ancestors.
     */
    fun walkAncestors(direction: WalkDirection = WalkDirection.BOTTOM_UP): Sequence<PathNode>

    /**
     * Returns a sequence of all the descendants of this path node.
     *
     * This walks through the tree of [children] depth-first regardless of which [direction] is being used. This path is
     * not included in the output.
     *
     * @param [direction] The direction in which to walk the tree.
     */
    fun walkChildren(direction: WalkDirection = WalkDirection.TOP_DOWN): Sequence<PathNode>

    /**
     * Returns whether this path starts with the path [other].
     *
     * @see [Path.startsWith]
     */
    fun startsWith(other: PathNode): Boolean = path.startsWith(other.path)

    /**
     * Returns whether this path ends with the path [other].
     *
     * @see [Path.endsWith]
     */
    fun endsWith(other: PathNode): Boolean = path.endsWith(other.path)

    /**
     * Returns a copy of [other] which is relative to this path.
     *
     * If this path is "/a/b" and [other] is "/a/b/c/d", then the resulting path will be "c/d".
     *
     * If this is an absolute path, then [other] must be an an absolute path. Similarly, if this is a relative path,
     * then [other] must also be a relative path.
     *
     * @throws [IllegalArgumentException] [other] is not a path that can be relativized against this path.
     */
    fun relativize(other: PathNode): PathNode

    /**
     * Returns a copy of [other] with this path as its ancestor.
     *
     * If this path is "/a/b", and [other] is "c/d", then the resulting path will be "/a/b/c/d".
     *
     * If [other] is absolute, then this method returns [other].
     */
    fun resolve(other: PathNode): PathNode

    /**
     * Returns an immutable representation of the difference between this directory and [other].
     *
     * @param [onError] A function that is called for each I/O error that occurs and determines how to handle them.
     *
     * The following exceptions can be passed to [onError]:
     * - [NoSuchFileException] A file in one of the directories was not found in the filesystem.
     * - [IOException]: Some other I/O error occurred.
     */
    fun diff(other: PathNode, onError: ErrorHandler = ::skipOnError): PathDiff

    /**
     * Returns whether the file represented by this path exists in the filesystem.
     *
     * @param [checkType] Check not only whether the file exists, but also whether the type of file matches the [type]
     * of this object.
     * @param [recursive] Check this path and all its descendants.
     */
    fun exists(checkType: Boolean = true, recursive: Boolean = false): Boolean

    /**
     * Creates the file represented by this path in the filesystem.
     *
     * What type of file is created is determined by the [type].
     *
     * @param [recursive] Create this file and all its descendants.
     *
     * @throws [IOException] An I/O error occurred while creating the file.
     */
    fun createFile(recursive: Boolean = false)
}