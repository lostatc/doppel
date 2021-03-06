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

package io.github.lostatc.doppel.filesystem

import io.github.lostatc.doppel.handlers.ErrorHandler
import io.github.lostatc.doppel.handlers.PathConverter
import io.github.lostatc.doppel.handlers.SimplePathConverter
import io.github.lostatc.doppel.handlers.ThrowHandler
import io.github.lostatc.doppel.path.MutablePathNode
import io.github.lostatc.doppel.path.PathNode
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileSystemLoopException
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * Adds [pathNode] to [viewNode] if [pathNode] starts with [viewNode].
 *
 * This function can be used to implement [FileSystemAction.applyView].
 */
fun addNodeToView(pathNode: PathNode, viewNode: MutablePathNode) {
    if (pathNode.startsWith(viewNode)) viewNode.addDescendant(pathNode.toMutablePathNode())
}

/**
 * Removes [pathNode] from [viewNode] if [pathNode] is a descendant of [viewNode].
 *
 * This function can be used to implement [FileSystemAction.applyView].
 */
fun removeNodeFromView(pathNode: PathNode, viewNode: MutablePathNode) {
    // We need this check in case the two nodes are associated with different file systems.
    if (pathNode.startsWith(viewNode)) viewNode.removeDescendant(pathNode.path)
}

/**
 * A change to apply to the file system.
 *
 * Implementations of this interface are immutable.
 */
interface FileSystemAction {
    /**
     * A function that is called for each error that occurs while changes are being applied which determines how to
     * handle them.
     */
    val errorHandler: ErrorHandler

    /**
     * Modifies [viewNode] to provide a view of what the file system will look like after [applyFileSystem] is called.
     *
     * After this is called, [viewNode] will match what the file system would look like if [applyFileSystem] were called
     * assuming there are no errors.
     *
     * The functions [addNodeToView] and [removeNodeFromView] can be used to implement this method.
     */
    fun applyView(viewNode: MutablePathNode)

    /**
     * Applies the change to the file system.
     */
    fun applyFileSystem()
}

/**
 * An action that recursively moves a file or directory from [source] to [target].
 *
 * By default, the files represented by [source] and [target] must belong to the same file system. By passing a function
 * to [pathConverter], it is possible to recursively move files between file systems.
 *
 * If [source] and [target] represent the same file, then nothing is moved.
 *
 * If the file to be moved is a symbolic link then the link itself, and not its target, is moved.
 *
 * Moving a file will copy its last modified time if supported by both file stores.
 *
 * This move may or may not be atomic. If it is not atomic and an exception is thrown, the state of the file system is
 * not defined.
 *
 * The following exceptions can be passed to [errorHandler]:
 * - [InvalidPathException]: A path could not be converted by [pathConverter].
 * - [NoSuchFileException]: There was an attempt to move a nonexistent file.
 * - [FileAlreadyExistsException]: The destination file already exists and [overwrite] is `false`.
 * - [AccessDeniedException]: There was an attempt to open a directory that didn't succeed.
 * - [FileSystemLoopException]: [followLinks] is `true` and a cycle of symbolic links was detected.
 * - [AtomicMoveNotSupportedException]: [atomic] is `true` but the move cannot be performed as an atomic file system
 *   operation.
 * - [IOException]: Some other I/O error occurred while moving.
 *
 * @property [source] A path node representing file or directory to move.
 * @property [target] A path node representing file or directory to move [source] to.
 * @property [overwrite] If a file or directory already exists at [target], replace it. If is is a directory and it is
 * not empty, it is deleted recursively.
 * @property [atomic] Perform the move as an atomic file system operation. If this is `true`, [overwrite] is ignored. If
 * the file at [target] exists, it is implementation-specific if it is replaced or an [IOException] is thrown.
 * @property [followLinks] Follow symbolic links when walking the directory tree.
 * @property [pathConverter] A function which is used to convert [Path] objects between file systems.
 */
data class MoveAction(
    val source: PathNode, val target: PathNode,
    val overwrite: Boolean = false,
    val atomic: Boolean = false,
    val followLinks: Boolean = false,
    val pathConverter: PathConverter = SimplePathConverter(),
    override val errorHandler: ErrorHandler = ThrowHandler()
) : FileSystemAction {
    override fun applyView(viewNode: MutablePathNode) {
        removeNodeFromView(source, viewNode)
        addNodeToView(target, viewNode)
    }

    override fun applyFileSystem() {
        moveRecursively(
            source.path, target.path,
            overwrite = overwrite, atomic = atomic, followLinks = followLinks,
            pathConverter = pathConverter, errorHandler = errorHandler
        )
    }
}

/**
 * An action that recursively copies a file or directory from [source] to [target].
 *
 * By default, the files represented by [source] and [target] must belong to the same file system. By passing a function
 * to [pathConverter], it is possible to recursively copy files between file systems.
 *
 * If [source] and [target] represent the same file, then nothing is copied.
 *
 * If the file to be copied is a symbolic link then the link itself, and not its target, is moved.
 *
 * Copying a file or directory is not an atomic operation. If an [IOException] is thrown, then the state of the
 * file system is undefined.
 *
 * The following exceptions can be passed to [errorHandler]:
 * - [InvalidPathException]: A path could not be converted by [pathConverter].
 * - [NoSuchFileException]: There was an attempt to copy a nonexistent file.
 * - [FileAlreadyExistsException]: The destination file already exists and [overwrite] is `false`.
 * - [AccessDeniedException]: There was an attempt to open a directory that didn't succeed.
 * - [FileSystemLoopException]: [followLinks] is `true` and a cycle of symbolic links was detected.
 * - [IOException]: Some other I/O error occurred while copying.
 *
 * @property [source] A path node representing the file or directory to copy.
 * @property [target] A path node representing the file or directory to copy [source] to.
 * @property [overwrite] If a file or directory already exists at [target], replace it. If is is a directory and it is
 * not empty, it is deleted recursively.
 * @property [copyAttributes] Attempt to copy file attributes from [source] to [target]. The last modified time is
 * always copied if supported. Whether other attributes are copied is platform and file system dependent. File attributes
 * of links may not be copied.
 * @property [followLinks] Follow symbolic links when walking the directory tree and copy the targets of links instead
 * of links themselves.
 * @property [pathConverter] A function which is used to convert [Path] objects between file systems.
 */
data class CopyAction(
    val source: PathNode, val target: PathNode,
    val overwrite: Boolean = false,
    val copyAttributes: Boolean = false,
    val followLinks: Boolean = false,
    val pathConverter: PathConverter = SimplePathConverter(),
    override val errorHandler: ErrorHandler = ThrowHandler()
) : FileSystemAction {
    override fun applyView(viewNode: MutablePathNode) {
        addNodeToView(target, viewNode)
    }

    override fun applyFileSystem() {
        copyRecursively(
            source.path, target.path,
            overwrite = overwrite, copyAttributes = copyAttributes, followLinks = followLinks,
            pathConverter = pathConverter, errorHandler = errorHandler
        )
    }
}

/**
 * An action that creates the file represented by [target].
 *
 * The following exceptions can be passed to [errorHandler]:
 * - [IOException]: Some I/O error occurred while creating the file.
 *
 * @property [target] The path node representing the file to create.
 * @property [recursive] Create this file and all its descendants.
 */
data class CreateAction(
    val target: PathNode,
    val recursive: Boolean = false,
    override val errorHandler: ErrorHandler = ThrowHandler()
) : FileSystemAction {
    override fun applyView(viewNode: MutablePathNode) {
        addNodeToView(target, viewNode)
    }

    override fun applyFileSystem() {
        target.createFile(recursive = recursive, errorHandler = errorHandler)
    }
}

/**
 * An action that recursively deletes the file or directory represented by [target].
 *
 * This operation is not atomic. Deleting an individual file or directory may not be atomic either.
 *
 * If the file to be deleted is a symbolic link then the link itself, and not its target, is deleted.
 *
 * The following exceptions can be passed to [errorHandler]:
 * - [NoSuchFileException]: There was an attempt to delete a nonexistent file.
 * - [AccessDeniedException]: There was an attempt to open a directory that didn't succeed.
 * - [FileSystemLoopException]: [followLinks] is `true` and a cycle of symbolic links was detected.
 * - [IOException]: Some other I/O error occurred while deleting.
 *
 * @property [target] A path node representing the file or directory to delete.
 * @property [followLinks] Follow symbolic links when walking the directory tree.
 */
data class DeleteAction(
    val target: PathNode,
    val followLinks: Boolean = false,
    override val errorHandler: ErrorHandler = ThrowHandler()
) : FileSystemAction {
    override fun applyView(viewNode: MutablePathNode) {
        removeNodeFromView(target, viewNode)
    }

    override fun applyFileSystem() {
        deleteRecursively(target.path, followLinks = followLinks, errorHandler = errorHandler)
    }
}