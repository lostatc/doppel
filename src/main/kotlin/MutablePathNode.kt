package diffir

import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

private class Entry(override val key: Path, override val value: MutablePathNode) : Map.Entry<Path, MutablePathNode>

/**
 * A mutable representation of a file or directory tree.
 */
class MutablePathNode(
    override val fileName: Path,
    override val parent: MutablePathNode?,
    override val type: FileType = UnknownType(),
    override val children: MutableMap<Path, MutablePathNode> = mutableMapOf()
) : PathNode {
    init {
        require(fileName.parent == null) { "The given file name must not have a parent." }

        require(parent == null || fileName.fileSystem == parent.path.fileSystem) {
            "The given file name must be associated with the same filesystem as the given parent."
        }

        // Make this path a child of its parent.
        parent?.children?.put(fileName, this)
    }

    override val root: MutablePathNode
        get() = walkAncestors().lastOrNull() ?: this

    override val path: Path
        get() = parent?.path?.resolve(fileName) ?: fileName

    override val descendants: Map<Path, MutablePathNode> = object : Map<Path, MutablePathNode> {
        override val entries: Set<Map.Entry<Path, MutablePathNode>>
            get() = walkChildren().map { Entry(it.path, it) }.toSet()

        override val keys: Set<Path>
            get() = walkChildren().map { it.path }.toSet()

        override val values: Collection<MutablePathNode>
            get() = walkChildren().toList()

        override val size: Int
            get() = entries.size

        override fun containsKey(key: Path): Boolean = get(key) != null

        override fun containsValue(value: MutablePathNode): Boolean = get(value.path) != null

        override operator fun get(key: Path): MutablePathNode? {
            if (!key.startsWith(path)) return null
            val relativeKey = path.relativize(key)

            return relativeKey.fold(this@MutablePathNode) { node, segment -> node.children[segment] ?: return null }
        }

        override fun isEmpty(): Boolean = children.isEmpty()
    }


    /**
     * Constructs a new path node from the given [path].
     *
     * This recursively sets the [parent] property so that a hierarchy of [MutablePathNode] objects going all the way up
     * to the root is returned. The root component of the path will be its own [MutablePathNode].
     */
    constructor(
        path: Path,
        type: FileType = UnknownType(),
        children: MutableMap<Path, MutablePathNode> = mutableMapOf()
    ) : this(
        fileName = path.fileName ?: path,
        parent = if (path.parent == null) null else MutablePathNode(path.parent),
        type = type,
        children = children
    )


    /**
     * Constructs a new path node from the given segments without path separators.
     *
     * The constructed node will be associated with the default filesystem.
     *
     * This recursively sets the [parent] property so that a hierarchy of [MutablePathNode] objects going all the way up
     * to the root is returned. The root component of the path will be its own [MutablePathNode].
     *
     * @param [firstSegment] The first segment of the path.
     * @param [segments] The remaining segments of the path.
     *
     * @see [Paths.get]
     */
    constructor(firstSegment: String, vararg segments: String) : this(Paths.get(firstSegment, *segments))

    override fun toString(): String = path.toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is PathNode) return false
        return fileName == other.fileName
            && parent == other.parent
            && type == other.type
            && children == other.children
    }

    override fun hashCode(): Int = Objects.hash(fileName, parent, type, children)

    /**
     * Returns a shallow copy of this node.
     *
     * @param [fileName] The new file name to assign to the copy.
     * @param [parent] The new parent to assign to the copy.
     * @param [type] The new file type to assign to the copy.
     */
    fun copy(
        fileName: Path = this.fileName,
        parent: MutablePathNode? = this.parent,
        type: FileType = this.type,
        children: MutableMap<Path, MutablePathNode> = this.children
    ): MutablePathNode {
        return MutablePathNode(fileName, parent, type, children)
    }

    override fun toPathNode(): PathNode = toMutablePathNode()

    override fun toMutablePathNode(): MutablePathNode {
        return copy(children = children.mapValues { it.value.toMutablePathNode() }.toMutableMap())
    }

    override fun walkAncestors(direction: WalkDirection): Sequence<MutablePathNode> = sequence {
        parent?.let {
            if (direction == WalkDirection.BOTTOM_UP) yield(it)
            yieldAll(it.walkAncestors(direction))
            if (direction == WalkDirection.TOP_DOWN) yield(it)
        }
    }

    override fun walkChildren(direction: WalkDirection): Sequence<MutablePathNode> = sequence {
        for (child in children.values) {
            if (direction == WalkDirection.TOP_DOWN) yield(child)
            yieldAll(child.walkChildren(direction))
            if (direction == WalkDirection.BOTTOM_UP) yield(child)
        }
    }

    override fun relativize(other: PathNode): MutablePathNode {
        require(other.startsWith(this)) { "The given path must start with this path." }
        require(path.isAbsolute == other.path.isAbsolute) {
            "Either both paths must be absolute or both paths must be relative."
        }

        // Copy the whole tree and set the parent to `null`.
        val childOfThisNode = other.walkAncestors().find { it.parent == this } ?: other
        val newRoot = childOfThisNode.toMutablePathNode().copy(parent = null)

        // Get the node with the same path as the node that was passed in.
        val relativePath = path.relativize(other.path)
        return newRoot.descendants[relativePath] ?: newRoot
    }

    override fun resolve(other: PathNode): MutablePathNode {
        if (other.path.isAbsolute) return other.toMutablePathNode()

        // Copy the whole tree and set the parent of the root node to this node.
        val childOfThisNode = other.root.toMutablePathNode().copy(parent = this)

        // Get the node with the same path as the node that was passed in.
        val fullPath = path.resolve(other.path)
        return childOfThisNode.descendants[fullPath] ?: childOfThisNode
    }

    override fun diff(other: PathNode, onError: ErrorHandler): PathDiff {

    }

    override fun exists(checkType: Boolean, recursive: Boolean): Boolean {

    }

    override fun createFile(recursive: Boolean) {

    }

    /**
     * Adds the given [pathNode] as a descendant of this node, inserting it into the tree.
     *
     * @return `true` if the node was added or `false` if it already exists.
     */
    fun addDescendant(pathNode: MutablePathNode): Boolean {
        val newAncestor = pathNode.path.fold(this) { node, segment -> node.children[segment] ?: node }
        val relativeNewNode = newAncestor.relativize(pathNode)
        val newNodeRoot = relativeNewNode.root
        return newAncestor.children.put(newNodeRoot.fileName, newNodeRoot) != null
    }

    /**
     * Adds the given [pathNodes] as descendants of this node, inserting them into the tree.
     *
     * @return `true` if any of the nodes were added or `false` if all of them already exist.
     */
    fun addAllDescendants(pathNodes: Collection<MutablePathNode>): Boolean =
        pathNodes.filter { addDescendant(it) }.any()

    /**
     * Removes the descendant with the given [path] from the tree.
     *
     * @return `true` if the descendant was removed or `false` if it doesn't exist.
     */
    fun removeDescendant(path: Path): Boolean {
        val nodeToRemove = descendants[path]
        return nodeToRemove?.parent?.children?.remove(path) != null
    }

    /**
     * Removes the descendants with the given [paths] from the tree.
     *
     * @return `true` any of the descendants were removed or `false` if none of them exist.
     */
    fun removeAllDescendants(paths: Collection<Path>): Boolean = paths.filter { removeDescendant(it) }.any()
}