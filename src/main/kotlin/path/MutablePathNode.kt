package diffir.path

import diffir.error.ErrorHandler
import diffir.error.ErrorHandlerAction
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Objects

private class Entry(override val key: Path, override val value: MutablePathNode) : Map.Entry<Path, MutablePathNode>

/**
 * A mutable representation of a tree of file paths.
 *
 * This [PathNode] implementation allows for modifying the tree of nodes in place using methods like [addDescendant],
 * [removeDescendant] and [clearChildren].
 *
 * @param [initialType] The initial type for this path node.
 */
class MutablePathNode(
    override val fileName: Path,
    override val parent: MutablePathNode? = null,
    private val initialType: FileType = UnknownType()
) : PathNode {
    init {
        require(fileName.parent == null) { "The given file name must not have a parent." }

        require(parent == null || fileName.fileSystem == parent.path.fileSystem) {
            "The given file name must be associated with the same filesystem as the given parent."
        }

        // Make this path a child of its parent.
        parent?._children?.put(fileName, this)
    }

    override val type: FileType
        get() = initialType.getFileType(this)

    override val root: MutablePathNode
        get() = walkAncestors().lastOrNull() ?: this

    override val path: Path
        get() = parent?.path?.resolve(fileName) ?: fileName

    private val _children: MutableMap<Path, MutablePathNode> = mutableMapOf()

    override val children: Map<Path, MutablePathNode> = _children

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

        override operator fun get(key: Path): MutablePathNode? = relativeDescendants[path.relativize(key)]

        override fun isEmpty(): Boolean = children.isEmpty()
    }

    override val relativeDescendants: Map<Path, MutablePathNode> = object : Map<Path, MutablePathNode> {
        override val entries: Set<Map.Entry<Path, MutablePathNode>>
            get() = walkChildren().map { Entry(path.relativize(it.path), it) }.toSet()

        override val keys: Set<Path>
            get() = walkChildren().map { path.relativize(it.path) }.toSet()

        override val values: Collection<MutablePathNode>
            get() = walkChildren().toList()

        override val size: Int
            get() = entries.size

        override fun containsKey(key: Path): Boolean = get(key) != null

        override fun containsValue(value: MutablePathNode): Boolean = get(value.path) != null

        override operator fun get(key: Path): MutablePathNode? =
            key.fold(this@MutablePathNode) { node, segment -> node.children[segment] ?: return null }

        override fun isEmpty(): Boolean = children.isEmpty()
    }

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
        type: FileType = this.type
    ): MutablePathNode {
        val newNode = MutablePathNode(fileName, parent, type)
        newNode.addAllDescendants(children.values)
        return newNode
    }

    override fun toPathNode(): PathNode = toMutablePathNode()

    override fun toMutablePathNode(): MutablePathNode {
        val newNode = MutablePathNode(fileName, parent, type)
        val copyOfChildren = children.values.map { it.toMutablePathNode() }
        newNode.addAllDescendants(copyOfChildren)
        return newNode
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

    override fun diff(other: PathNode, onError: ErrorHandler): PathDiff = PathDiff.fromPathNodes(this, other, onError)

    override fun exists(checkType: Boolean, recursive: Boolean): Boolean {
        val fileExists = if (checkType) type.checkType(path) else Files.exists(path)

        return if (recursive) {
            fileExists && walkChildren().all { it.exists(checkType, false) }
        } else {
            fileExists
        }
    }

    override fun sameContentsAs(other: PathNode): Boolean {
        if (type != other.type) return false
        return type.checkSame(path, other.path)
    }

    override fun createFile(recursive: Boolean, onError: ErrorHandler) {
        var nodesToCreate = sequenceOf(this)
        if (recursive) nodesToCreate += walkChildren()

        create@ for (node in nodesToCreate) {
            try {
                node.type.createFile(node.path)
            } catch (e: IOException) {
                when (onError(node.path, e)) {
                    ErrorHandlerAction.SKIP -> continue@create
                    ErrorHandlerAction.TERMINATE -> break@create
                }
            }
        }
    }

    /**
     * Adds the given [pathNode] as a descendant of this node, inserting it into the tree.
     *
     * @return `true` if the node was added or `false` if it already exists.
     */
    fun addDescendant(pathNode: MutablePathNode): Boolean {
        // Get the descendant of this node that will be the ancestor of the given node.
        val newAncestor = pathNode.path.fold(this) { node, segment -> node.children[segment] ?: node }

        val relativeNewNode = newAncestor.relativize(pathNode)
        val newNodeRoot = relativeNewNode.root

        return newAncestor._children.put(newNodeRoot.fileName, newNodeRoot) != null
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
        return nodeToRemove?.parent?._children?.remove(path) != null
    }

    /**
     * Removes the descendants with the given [paths] from the tree.
     *
     * @return `true` any of the descendants were removed or `false` if none of them exist.
     */
    fun removeAllDescendants(paths: Collection<Path>): Boolean = paths.filter { removeDescendant(it) }.any()

    /**
     * Removes all children from this node.
     */
    fun clearChildren() {
        _children.clear()
    }

    companion object : PathNodeFactory {
        override fun of(path: Path, type: FileType, init: MutablePathNode.() -> Unit): MutablePathNode {
            val fileName = path.fileName ?: path
            val parent = if (path.parent == null) null else MutablePathNode.of(path.parent)
            val pathNode = MutablePathNode(fileName, parent, type)
            pathNode.init()
            return pathNode
        }

        override fun of(
            firstSegment: String, vararg segments: String,
            type: FileType,
            init: MutablePathNode.() -> Unit
        ): MutablePathNode {
            val path = Paths.get(firstSegment, *segments)
            return MutablePathNode.of(path, type = type, init = init)
        }

        override fun fromFilesystem(path: Path, recursive: Boolean, typeFactory: (Path) -> FileType): MutablePathNode {
            val newNode = MutablePathNode.of(path, type = typeFactory(path))

            if (recursive) {
                for (descendantPath in Files.walk(path)) {
                    val newDescendant = MutablePathNode.of(descendantPath, type = typeFactory(descendantPath))
                    newNode.addDescendant(newDescendant)
                }
            }

            return newNode
        }
    }
}