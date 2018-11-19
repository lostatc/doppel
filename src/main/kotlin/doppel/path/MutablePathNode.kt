/*
 * Copyright © 2018 Garrett Powell <garrett@gpowell.net>
 *
 * This file is part of doppel.
 *
 * doppel is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * doppel is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with doppel.  If not, see <https://www.gnu.org/licenses/>.
 */

package doppel.path

import doppel.error.ErrorHandler
import doppel.error.ErrorHandlerAction
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
 * [addRelativeDescendant], [removeDescendant] and [clearChildren]. Any methods or property setters which modify this
 * path node will ensure that each path node is always a child of its parent.
 *
 * @param [fileName] The file name for this path node.
 * @param [parent] The parent node for this path node.
 * @param [type] The initial type for this path node.
 */
class MutablePathNode(
    override val fileName: Path,
    parent: MutablePathNode? = null,
    type: FileType = UnknownType()
) : PathNode {
    init {
        require(fileName.parent == null) { "The given file name must not have a parent." }

        require(parent?.let { fileName.fileSystem == it.path.fileSystem } ?: true) {
            "The given file name must be associated with the same filesystem as the given parent."
        }

        // Make this path a child of its parent.
        parent?._children?.put(fileName, this)
    }

    override var parent: MutablePathNode? = parent
        set(value) {
            // Remove this node from the children of the old parent.
            field?._children?.remove(fileName)

            // Add this node to the children of the new parent.
            value?._children?.put(fileName, this)

            field = value
        }

    override var type: FileType = type
        get() = field.getFileType(this)

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
        if (other !is PathNode) return false
        return path == other.path && type == other.type && children == other.children
    }

    override fun hashCode(): Int = Objects.hash(path, type, children)

    override fun toPathNode(): PathNode = toMutablePathNode()

    override fun toMutablePathNode(): MutablePathNode {
        fun deeplyCopy(node: MutablePathNode, newParent: MutablePathNode?): MutablePathNode {
            val newNode = MutablePathNode(node.fileName, newParent, node.type)
            node.children.values.map { deeplyCopy(it, newNode) }
            return newNode
        }

        // We need to start at the root and work our way down the tree to copy ancestors while avoiding loops.
        val newRoot = deeplyCopy(root, null)
        return newRoot.descendants.getOrDefault(path, newRoot)
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
        val childOfThisNode = other.walkAncestors().find { it.parent?.path == path } ?: other
        val newRoot = childOfThisNode.toMutablePathNode()
        newRoot.parent = null

        // Get the node with the same path as the node that was passed in.
        val relativePath = path.relativize(other.path)
        return newRoot.descendants[relativePath] ?: newRoot
    }

    override fun resolve(other: PathNode): MutablePathNode {
        if (other.path.isAbsolute) return other.toMutablePathNode()

        // Copy the whole tree and set the parent of the root node to this node.
        val childOfThisNode = other.root.toMutablePathNode()
        childOfThisNode.parent = this

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
        if (type::class != other.type::class) return false
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
     * If a node with the same path as the given node already exists, it is replaced.
     *
     * @throws [IllegalArgumentException] The given path node does not start with this node.
     */
    fun addDescendant(pathNode: MutablePathNode) {
        require(pathNode.startsWith(this)) { "The given path must start with this path." }
        require(pathNode.path != path) { "The given path must not be equal to this path." }

        // Get the descendant of this node that will be the ancestor of the given node.
        val ancestorOfNewNode = pathNode.parent?.path?.fold(this) { node, segment ->
            node.children.getOrDefault(segment, node)
        } ?: this

        // Get the ancestor of the given node that will become an immediate child of [ancestorOfNewNode].
        val newNodeRoot = pathNode.walkAncestors().find { it.parent?.path == ancestorOfNewNode.path } ?: pathNode

        // Insert the new node into the tree.
        newNodeRoot.parent = ancestorOfNewNode
    }

    /**
     * Adds the given [pathNode] as a descendant of this node, inserting it into the tree.
     *
     * The given path node is assumed to be relative to this node. If a node with the same path as the given node
     * already exists, it is replaced.
     *
     * @throws [IllegalArgumentException] The given path node is absolute.
     */
    fun addRelativeDescendant(pathNode: MutablePathNode) {
        require(!pathNode.path.isAbsolute) { "The given path node must not be absolute" }

        // Get the descendant of this node that will be the ancestor of the given node.
        val fullPath = path.resolve(pathNode.path)
        val ancestorOfNewNode = fullPath.parent?.fold(this) { node, segment ->
            node.children.getOrDefault(segment, node)
        } ?: this

        // Get the ancestor of the given node that will become an immediate child of [ancestorOfNewNode].
        val newNodeRoot = pathNode.walkAncestors().find {
            path.resolve(it.parent?.path) == ancestorOfNewNode.path
        } ?: pathNode

        // Insert the new node into the tree.
        newNodeRoot.parent = ancestorOfNewNode
    }

    /**
     * Removes the descendant with the given [path] from the tree.
     *
     * @return The path node that was removed or `null` if it doesn't exist.
     */
    fun removeDescendant(path: Path): MutablePathNode? {
        val nodeToRemove = descendants[path] ?: return null
        nodeToRemove.parent = null
        return nodeToRemove
    }

    /**
     * Removes the descendant with the given [path] from the tree.
     *
     * The given [path] is assumed to be relative to this node.
     *
     * @return The path node that was removed or `null` if it doesn't exist.
     */
    fun removeRelativeDescendant(path: Path): MutablePathNode? {
        val nodeToRemove = relativeDescendants[path] ?: return null
        nodeToRemove.parent = null
        return nodeToRemove
    }

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
                // Skip the path itself. We only want its descendants.
                for (descendantPath in Files.walk(path).skip(1)) {
                    val newDescendant = MutablePathNode.of(descendantPath, type = typeFactory(descendantPath))
                    newNode.addDescendant(newDescendant)
                }
            }

            return newNode
        }
    }
}