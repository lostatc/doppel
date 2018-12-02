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

package io.github.lostatc.doppel.path

import io.github.lostatc.doppel.handlers.ErrorHandler
import io.github.lostatc.doppel.handlers.ErrorHandlerAction
import io.github.lostatc.doppel.handlers.throwOnError
import io.github.lostatc.doppel.path.PathNode.Companion.fromFileSystem
import io.github.lostatc.doppel.path.PathNode.Companion.of
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Objects

/**
 * Possible orders in which hierarchical data can be iterated over.
 */
enum class WalkDirection {
    /**
     * The data is iterated over from the root to the leaves.
     */
    TOP_DOWN,

    /**
     * The data is iterated over from the leaves to the root.
     */
    BOTTOM_UP
}

/**
 * A read-only representation of a tree of file paths.
 *
 * Objects of this type wrap a [Path] object to allow them to form a tree of file paths. This allows file hierarchies to
 * be represented and manipulated in memory. [PathNode] objects are read-only, while [MutablePathNode] objects allow the
 * tree of paths to be modified.
 *
 * [PathNode] objects work like a prefix tree, where each [PathNode] stores only a single path segment as [fileName].
 * The parent node can be accessed through the [parent] property and a map of child nodes can be accessed through the
 * [children] property. The full [Path] can be accessed through the [path] property.
 *
 * The properties [descendants] and [relativeDescendants] can be used to efficiently find descendants of this node by
 * their [Path].
 *
 * Each [PathNode] has a [type], which indicates the type of file the node represents in the file system. An initial type
 * is provided through the constructor, but this type can change based on the state of the node. For example, if the
 * [type] of a node is a regular file, then it will change to a directory if children are added. Custom file types with
 * custom behavior can be created by implementing [FileType].
 *
 * You can use [PathDiff] to get a comparision of two [PathNode] objects.
 *
 * [PathNode] objects can be created using the builder method [of] which provides a DSL for specifying what the
 * directory tree should look like. They can also be created by walking a directory tree in the file system using
 * [fromFileSystem].
 */
sealed class PathNode {
    /**
     * The name of the file or directory represented by this node.
     */
    abstract val fileName: Path

    /**
     * The parent node or `null` if there is no parent.
     */
    abstract val parent: PathNode?

    /**
     * The type of file represented by this node.
     */
    abstract val type: FileType

    /**
     * The ancestor whose [parent] is `null`, which could be this node.
     */
    abstract val root: PathNode

    /**
     * A [Path] representing this node.
     *
     * This is computed using [fileName] and [parent].
     */
    abstract val path: Path

    /**
     * A map of file names to path nodes for the immediate children of this node.
     */
    abstract val children: Map<Path, PathNode>

    /**
     * A map of file paths to path nodes for all the descendants of this node.
     */
    abstract val descendants: Map<Path, PathNode>

    /**
     * A map of relative file paths to path nodes for all the descendants of this node.
     *
     * Keys in this map are paths relative to this path node.
     */
    abstract val relativeDescendants: Map<Path, PathNode>

    /**
     * Returns the string representation of this node.
     */
    abstract override fun toString(): String

    /**
     * Indicates wither the object [other] is equal to this one.
     *
     * Two [PathNode] objects are equal if they have the same [path], [type] and [children].
     */
    abstract override operator fun equals(other: Any?): Boolean

    /**
     * Returns a hash code value for the object.
     */
    abstract override fun hashCode(): Int

    /**
     * Returns a deep copy of this object as a [PathNode] object.
     */
    abstract fun toPathNode(): PathNode

    /**
     * Returns a deep copy of this object as a [MutablePathNode] object.
     */
    abstract fun toMutablePathNode(): MutablePathNode

    /**
     * Returns a sequence of all the ancestors of this node.
     *
     * This node is not included in the sequence.
     *
     * @param [direction] The direction in which to iterate over ancestors.
     */
    abstract fun walkAncestors(direction: WalkDirection = WalkDirection.BOTTOM_UP): Sequence<PathNode>

    /**
     * Returns a sequence of all the descendants of this node.
     *
     * This walks through the tree of [children] depth-first regardless of which [direction] is being used. This node is
     * not included in the sequence.
     *
     * @param [direction] The direction in which to walk the tree.
     */
    abstract fun walkChildren(direction: WalkDirection = WalkDirection.TOP_DOWN): Sequence<PathNode>

    /**
     * Returns whether the path represented by this node starts with the path represented by [other].
     *
     * @see [Path.startsWith]
     */
    abstract fun startsWith(other: PathNode): Boolean

    /**
     * Returns whether the path represented by this node ends with the path represented by [other].
     *
     * @see [Path.endsWith]
     */
    abstract fun endsWith(other: PathNode): Boolean

    /**
     * Returns a deep copy of [other] which is relative to this path node.
     *
     * If this path node is "/a/b" and [other] is "/a/b/c/d", then the resulting path node will be "c/d".
     *
     * If the path represented by this node is absolute, then [other] must be absolute. Similarly, if the path
     * represented by this node is relative, then [other] must also be relative.
     *
     * @throws [IllegalArgumentException] [other] is not a path node that can be relativized against this node.
     */
    abstract fun relativize(other: PathNode): PathNode

    /**
     * Returns a deep copy of [other] with this node as its ancestor.
     *
     * If this path node is "/a/b", and [other] is "c/d", then the resulting path node will be "/a/b/c/d".
     *
     * If [other] is absolute, then this method returns a deep copy of [other].
     */
    abstract fun resolve(other: PathNode): PathNode

    /**
     * Returns a deep copy of [other] with this node as its ancestor.
     *
     * This is an operator method that calls [resolve].
     */
    abstract operator fun div(other: PathNode): PathNode

    /**
     * Returns a deep copy of this node that is absolute.
     *
     * If this node is absolute, then this method returns a deep copy of this node.
     *
     * @throws [IOException] An I/O error occurred.
     *
     * @see [Path.toAbsolutePath]
     */
    abstract fun toAbsoluteNode(): PathNode

    /**
     * Returns whether the file represented by this path node exists in the file system.
     *
     * @param [checkType] Check not only whether the file exists, but also whether the type of file matches the [type]
     * of its node.
     * @param [recursive] Check this node and all its descendants.
     */
    abstract fun exists(checkType: Boolean = true, recursive: Boolean = false): Boolean

    /**
     * Returns whether the files represented by this path node and [other] have the same contents.
     *
     * How file contents are compared is determined by the [type]. If the [type] of each node is different, this returns
     * `false`.
     */
    abstract fun sameContentsAs(other: PathNode): Boolean

    /**
     * Creates the file represented by this path node in the file system.
     *
     * What type of file is created is determined by the [type].
     *
     * The following exceptions can be passed to [onError]:
     * - [IOException]: Some I/O error occurred while creating the file.
     *
     * @param [recursive] Create this file and all its descendants.
     * @param [onError] A function that is called for each error that occurs and determines how to handle them.
     */
    abstract fun createFile(recursive: Boolean = false, onError: ErrorHandler = ::throwOnError)

    companion object : PathNodeFactory {
        override fun of(path: Path, type: FileType, init: MutablePathNode.() -> Unit): PathNode =
            MutablePathNode.of(path, type, init)

        override fun of(
            firstSegment: String, vararg segments: String,
            type: FileType,
            init: MutablePathNode.() -> Unit
        ): PathNode {
            return MutablePathNode.of(firstSegment, *segments, type = type, init = init)
        }

        override fun fromFileSystem(path: Path, recursive: Boolean, typeFactory: (Path) -> FileType): PathNode =
            MutablePathNode.fromFileSystem(path, recursive, typeFactory)

    }
}

private class Entry(override val key: Path, override val value: MutablePathNode) : Map.Entry<Path, MutablePathNode>

/**
 * A mutable representation of a tree of file paths.
 *
 * This [PathNode] implementation allows for modifying the tree of nodes in place using methods like [addDescendant],
 * [addRelativeDescendant], [removeDescendant], [removeRelativeDescendant] and [clearChildren]. Any methods or property
 * setters that modify this path node will ensure that each path node is always a child of its parent.
 *
 * @param [fileName] The file name for this path node.
 * @param [parent] The parent node for this path node.
 * @param [type] The initial type for this path node.
 */
class MutablePathNode(
    override val fileName: Path,
    parent: MutablePathNode? = null,
    type: FileType = UnknownType()
) : PathNode() {
    init {
        require(fileName.parent == null) { "The given file name must not have a parent." }

        require(parent?.let { fileName.fileSystem == it.path.fileSystem } ?: true) {
            "The given file name must be associated with the same file system as the given parent."
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

    override fun startsWith(other: PathNode): Boolean = path.startsWith(other.path)

    override fun endsWith(other: PathNode): Boolean = path.endsWith(other.path)

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

    override operator fun div(other: PathNode): MutablePathNode = resolve(other)

    override fun toAbsoluteNode(): MutablePathNode {
        if (path.isAbsolute) return toMutablePathNode()

        val newAncestor = MutablePathNode.of(root.path.toAbsolutePath())
        return newAncestor.parent?.resolve(this) ?: toMutablePathNode()
    }

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
            it.parent == null || path.resolve(it.parent?.path) == ancestorOfNewNode.path
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
            val parent = if (path.parent == null) null else of(path.parent)
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
            return of(path, type = type, init = init)
        }

        override fun fromFileSystem(path: Path, recursive: Boolean, typeFactory: (Path) -> FileType): MutablePathNode {
            val newNode = of(path, type = typeFactory(path))

            if (recursive) {
                // Skip the path itself. We only want its descendants.
                for (descendantPath in Files.walk(path).skip(1)) {
                    val newDescendant = of(descendantPath, type = typeFactory(descendantPath))
                    newNode.addDescendant(newDescendant)
                }
            }

            return newNode
        }
    }
}

/**
 * A factory for creating [PathNode] instances.
 */
internal interface PathNodeFactory {
    /**
     * Constructs a new path node from the given [path] and its children.
     *
     * This method is a type-safe builder. It allows you to create a tree of path nodes of specified types. The [init]
     * parameter accepts a lambda in which you can call builder methods like [file], [dir], [symlink] and [unknown] to
     * create new path nodes as children of this node. Builder methods can be created for custom [FileType]
     * implementations using [pathNode].
     *
     * The whole tree of path nodes will be associated with the same file system as [path].
     *
     * Example:
     * ```
     * val path = Paths.get("/", "home", "user")
     *
     * val pathNode = PathNode.of(path) {
     *     file("Photo.png")
     *     dir("Documents", "Reports") {
     *         file("Monthly.odt")
     *         file("Quarterly.odt")
     *     }
     * }
     * ```
     *
     * @param [path] The path to construct the new path node from.
     * @param [type] The initial type of the created node.
     * @param [init] A function with receiver in which you can call builder methods to construct children.
     *
     * @return A new path node containing the given children.
     */
    fun of(
        path: Path,
        type: FileType = UnknownType(),
        init: MutablePathNode.() -> Unit = {}
    ): PathNode

    /**
     * Constructs a new path node from the given path segments.
     *
     * This is an overload of [of] that accepts strings that form a path when joined. The constructed file node is
     * associated with the default file system.
     *
     * @see [Paths.get]
     */
    fun of(
        firstSegment: String, vararg segments: String,
        type: FileType = UnknownType(),
        init: MutablePathNode.() -> Unit = {}
    ): PathNode

    /**
     * Constructs a new path node from files in the file system.
     *
     * This method constructs a new node from the given [path] and gets the [type][PathNode.type] of the node from the
     * file system. If [recursive] is `true` it also gets all descendants of the given [path] from the file system and
     * creates nodes for them, returning a tree of nodes.
     *
     * @param [path] The path to construct the new node from.
     * @param [recursive] Create a tree of nodes recursively.
     * @param [typeFactory] A function that determines which [FileType] to assign to each path node.
     */
    fun fromFileSystem(
        path: Path,
        recursive: Boolean = false,
        typeFactory: (Path) -> FileType = ::fileTypeFromFileSystem
    ): PathNode
}