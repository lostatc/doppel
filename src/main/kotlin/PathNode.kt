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
 * parent node can be accessed through the [parent] property and a map of child nodes can be accessed through the
 * [children] property. The full [Path] can be accessed through the [path] property.
 *
 * Each [PathNode] has a [type], which indicates the type of file the node represents in the filesystem.
 */
interface PathNode {
    /**
     * The name of the file or directory represented by this node.
     */
    val fileName: Path

    /**
     * The parent node or `null` if there is no parent.
     */
    val parent: PathNode?

    /**
     * The type of file represented by this node.
     */
    val type: FileType

    /**
     * The ancestor whose [parent] is `null`, which could be this node.
     */
    val root: PathNode

    /**
     * A [Path] representing this node.
     *
     * This is computed using [fileName] and [parent].
     */
    val path: Path

    /**
     * A map of file names to path nodes for the immediate children of this node.
     */
    val children: Map<Path, PathNode>

    /**
     * A map of file paths to path nodes for all the descendants of this node.
     */
    val descendants: Map<Path, PathNode>

    /**
     * Returns the string representation of this node.
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
     * Returns a sequence of all the ancestors of this node.
     *
     * @param [direction] The direction in which to iterate over ancestors.
     */
    fun walkAncestors(direction: WalkDirection = WalkDirection.BOTTOM_UP): Sequence<PathNode>

    /**
     * Returns a sequence of all the descendants of this node.
     *
     * This walks through the tree of [children] depth-first regardless of which [direction] is being used. This node is
     * not included in the sequence.
     *
     * @param [direction] The direction in which to walk the tree.
     */
    fun walkChildren(direction: WalkDirection = WalkDirection.TOP_DOWN): Sequence<PathNode>

    /**
     * Returns whether the path represented by this node starts with the path represented by [other].
     *
     * @see [Path.startsWith]
     */
    fun startsWith(other: PathNode): Boolean = path.startsWith(other.path)

    /**
     * Returns whether the path represented by this node ends with the path represented by [other].
     *
     * @see [Path.endsWith]
     */
    fun endsWith(other: PathNode): Boolean = path.endsWith(other.path)

    /**
     * Returns a copy of [other] which is relative to this path node.
     *
     * If this path node is "/a/b" and [other] is "/a/b/c/d", then the resulting path node will be "c/d".
     *
     * If the path represented by this node is absolute, then [other] must be absolute. Similarly, if the path
     * represented by this node is relative, then [other] must also be relative.
     *
     * @throws [IllegalArgumentException] [other] is not a path node that can be relativized against this node.
     */
    fun relativize(other: PathNode): PathNode

    /**
     * Returns a copy of [other] with this node as its ancestor.
     *
     * If this path node is "/a/b", and [other] is "c/d", then the resulting path node will be "/a/b/c/d".
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
     * Returns whether the file represented by this path node exists in the filesystem.
     *
     * @param [checkType] Check not only whether the file exists, but also whether the type of file matches the [type]
     * of this object.
     * @param [recursive] Check this node and all its descendants.
     */
    fun exists(checkType: Boolean = true, recursive: Boolean = false): Boolean

    /**
     * Creates the file represented by this path node in the filesystem.
     *
     * What type of file is created is determined by the [type].
     *
     * @param [recursive] Create this file and all its descendants.
     *
     * @throws [IOException] An I/O error occurred while creating the file.
     */
    fun createFile(recursive: Boolean = false)
}