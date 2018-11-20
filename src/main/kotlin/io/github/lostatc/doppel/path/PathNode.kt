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

import io.github.lostatc.doppel.error.ErrorHandler
import io.github.lostatc.doppel.error.skipOnError
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths

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
 * be represented and manipulated in memory.
 *
 * This class works like a prefix tree, where each [PathNode] stores only a single path segment as [fileName]. The
 * parent node can be accessed through the [parent] property and a map of child nodes can be accessed through the
 * [children] property. The full [Path] can be accessed through the [path] property.
 *
 * Each [PathNode] has a [type], which indicates the type of file the node represents in the filesystem. An initial type
 * is provided through the constructor, but this type can change based on the state of the node. For example, if the
 * [type] of a node is a regular file, then it will change to a directory if children are added. Custom file types with
 * custom behavior can be created by implementing [FileType].
 *
 * *Warning:* This interface is not meant to be implemented. New members may be added in the future.
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
     * A map of relative file paths to path nodes for all the descendants of this node.
     *
     * Keys in this map are paths relative to this path node.
     */
    val relativeDescendants: Map<Path, PathNode>

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
     * Children and ancestors are copied deeply.
     */
    fun toPathNode(): PathNode

    /**
     * Returns a copy of this object as a [MutablePathNode] object.
     *
     * Children and ancestors are copied deeply.
     */
    fun toMutablePathNode(): MutablePathNode

    /**
     * Returns a sequence of all the ancestors of this node.
     *
     * This node is not included in the sequence.
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
     * If [other] is absolute, then this method returns a copy of [other].
     */
    fun resolve(other: PathNode): PathNode

    /**
     * Returns an immutable representation of the difference between this directory and [other].
     *
     * The following exceptions can be passed to [onError]:
     * - [NoSuchFileException] A file in one of the directories was not found in the filesystem.
     * - [IOException]: Some other I/O error occurred.
     *
     * @param [other] The path node to compare this node with.
     * @param [onError] A function that is called for each I/O error that occurs and determines how to handle them.
     */
    fun diff(other: PathNode, onError: ErrorHandler = ::skipOnError): PathDiff

    /**
     * Returns whether the file represented by this path node exists in the filesystem.
     *
     * @param [checkType] Check not only whether the file exists, but also whether the type of file matches the [type]
     * of its node.
     * @param [recursive] Check this node and all its descendants.
     */
    fun exists(checkType: Boolean = true, recursive: Boolean = false): Boolean

    /**
     * Returns whether the files represented by this path node and [other] have the same contents.
     *
     * How files are compared is determined by the [type]. If the [type] of each node is different, this returns
     * `false`.
     */
    fun sameContentsAs(other: PathNode): Boolean

    /**
     * Creates the file represented by this path node in the filesystem.
     *
     * What type of file is created is determined by the [type].
     *
     * The following exceptions can be passed to [onError]:
     * - [IOException]: Some I/O error occurred while creating the file.
     *
     * @param [recursive] Create this file and all its descendants.
     * @param [onError] A function that is called for each I/O error that occurs and determines how to handle them.
     */
    fun createFile(recursive: Boolean = false, onError: ErrorHandler = ::skipOnError)

    companion object : PathNodeFactory {
        override fun of(path: Path, type: FileType, init: MutablePathNode.() -> Unit): PathNode {
            return MutablePathNode.of(path, type, init)
        }

        override fun of(
            firstSegment: String, vararg segments: String,
            type: FileType,
            init: MutablePathNode.() -> Unit
        ): PathNode {
            return MutablePathNode.of(
                firstSegment,
                *segments,
                type = type,
                init = init
            )
        }

        override fun fromFilesystem(path: Path, recursive: Boolean, typeFactory: (Path) -> FileType): PathNode {
            return MutablePathNode.fromFilesystem(path, recursive, typeFactory)
        }

    }
}

/**
 * A factory for creating [PathNode] instances.
 *
 * *Warning:* This interface is not meant to be implemented. New members may be added in the future.
 */
interface PathNodeFactory {
    /**
     * Constructs a new path node from the given [path] and its children.
     *
     * This method is a type-safe builder. It allows you to create a tree of path nodes of specified types. The
     * [init] parameter accepts a lambda in which you can call builder methods like [file], [dir], [symlink] and
     * [unknown] to create new path nodes as children of this node. Builder methods can be created for custom
     * [FileType] classes using [pathNode].
     *
     * The whole tree of path nodes will be associated with the same filesystem as [path].
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
     * associated with the default filesystem.
     *
     * @see [Paths.get]
     */
    fun of(
        firstSegment: String, vararg segments: String,
        type: FileType = UnknownType(),
        init: MutablePathNode.() -> Unit = {}
    ): PathNode

    /**
     * Constructs a new path node from files in the filesystem.
     *
     * This method constructs a new node from the given [path] and gets the [type][PathNode.type] of the node from the
     * filesystem. If [recursive] is `true` it also gets all descendants of the given [path] from the filesystem and
     * creates nodes for them, returning a tree of nodes.
     *
     * @param [path] The path to construct the new node from.
     * @param [recursive] Create a tree of nodes recursively.
     * @param [typeFactory] A function that determines which [FileType] to assign to each path node.
     */
    fun fromFilesystem(
        path: Path,
        recursive: Boolean = false,
        typeFactory: (Path) -> FileType = ::fileTypeFromFilesystem
    ): PathNode
}