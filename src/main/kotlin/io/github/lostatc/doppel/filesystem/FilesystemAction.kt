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

import io.github.lostatc.doppel.error.ErrorHandler
import io.github.lostatc.doppel.error.skipOnError
import io.github.lostatc.doppel.path.MutablePathNode
import io.github.lostatc.doppel.path.PathNode
import java.io.IOException
import java.nio.file.FileSystemLoopException
import java.nio.file.ProviderMismatchException

/**
 * Adds [pathNode] to [viewNode] if [pathNode] starts with [viewNode].
 *
 * This function can be used to implement [FilesystemAction.applyView].
 */
fun addNodeToView(viewNode: MutablePathNode, pathNode: PathNode) {
    if (pathNode.startsWith(viewNode)) viewNode.addDescendant(pathNode.toMutablePathNode())
}

/**
 * Removes [pathNode] from [viewNode] if [pathNode] is a descendant of [viewNode].
 *
 * This function can be used to implement [FilesystemAction.applyView].
 */
fun removeNodeFromView(viewNode: MutablePathNode, pathNode: PathNode) {
    // We need this check in case the two nodes are associated with different filesystems.
    if (pathNode.startsWith(viewNode)) viewNode.removeDescendant(pathNode.path)
}

/**
 * A change to apply to the filesystem.
 */
interface FilesystemAction {
    /**
     * A function that is called for each error that occurs while changes are being applied which determines how to
     * handle them.
     */
    val onError: ErrorHandler

    /**
     * Modifies [viewNode] to provide a view of what the filesystem will look like after [applyFilesystem] is called.
     *
     * After this is called, [viewNode] will match what the filesystem would look like if [applyFilesystem] were called
     * assuming there are no errors.
     *
     * The functions [addNodeToView] and [removeNodeFromView] can be used to implement this method.
     */
    fun applyView(viewNode: MutablePathNode)

    /**
     * Applies the change to the filesystem.
     */
    fun applyFilesystem()
}

/**
 * An action that recursively moves a file or directory from [source] to [target].
 *
 * The files represented by [source] and [target] must belong to the same filesystem.
 *
 * If [source] and [target] represent the same file, then nothing is moved.
 *
 * If the file to be moved is a symbolic link then the link itself, and not its target, is moved.
 *
 * Moving a file will copy its last modified time if supported by both file stores.
 *
 * This move may or may not be atomic. If it is not atomic and an exception is thrown, the state of the filesystem is
 * not defined.
 *
 * The following exceptions can be passed to [onError]:
 * - [NoSuchFileException]: There was an attempt to move a nonexistent file.
 * - [FileAlreadyExistsException]: The destination file already exists and [overwrite] is `false`.
 * - [AccessDeniedException]: There was an attempt to open a directory that didn't succeed.
 * - [FileSystemLoopException]: [followLinks] is `true` and a cycle of symbolic links was detected.
 * - [IOException]: Some other problem occurred while moving.
 *
 * @property [source] A path node representing file or directory to move.
 * @property [target] A path node representing file or directory to move [source] to.
 * @property [overwrite] If a file or directory already exists at [target], replace it. If the directory is not empty,
 * it is deleted recursively.
 * @property [followLinks] Follow symbolic links when walking the directory tree.
 */
data class MoveAction(
    val source: PathNode, val target: PathNode,
    val overwrite: Boolean = false,
    val followLinks: Boolean = false,
    override val onError: ErrorHandler = ::skipOnError
) : FilesystemAction {
    init {
        if (source.path.fileSystem != target.path.fileSystem)
            throw ProviderMismatchException("The source and target paths must belong to the same filesystem.")
    }

    override fun applyView(viewNode: MutablePathNode) {
        removeNodeFromView(viewNode, source)
        addNodeToView(viewNode, target)
    }

    override fun applyFilesystem() {
        moveRecursively(source.path, target.path, overwrite = overwrite, followLinks = followLinks, onError = onError)
    }
}

/**
 * An action that recursively copies a file or directory from [source] to [target].
 *
 * The files represented by [source] and [target] must belong to the same filesystem.
 *
 * If [source] and [target] represent the same file, then nothing is copied.
 *
 * Copying a file or directory is not an atomic operation. If an [IOException] is thrown, then the state of the
 * filesystem is undefined.
 *
 * The following exceptions can be passed to [onError]:
 * - [NoSuchFileException]: There was an attempt to copy a nonexistent file.
 * - [FileAlreadyExistsException]: The destination file already exists.
 * - [AccessDeniedException]: There was an attempt to open a directory that didn't succeed.
 * - [FileSystemLoopException]: [followLinks] is `true` and a cycle of symbolic links was detected.
 * - [IOException]: Some other problem occurred while copying.
 *
 * @property [source] A path node representing the file or directory to copy.
 * @property [target] A path node representing the file or directory to copy [source] to.
 * @property [overwrite] If a file or directory already exists at [target], replace it. If the directory is not empty,
 * it is deleted recursively.
 * @property [copyAttributes] Attempt to copy file attributes from [source] to [target]. The last modified time is
 * always copied if supported. Whether other attributes are copied is platform and filesystem dependent. File attributes
 * of links may not be copied.
 * @property [followLinks] Follow symbolic links when walking the directory tree and copy the targets of links instead
 * of links themselves.
 */
data class CopyAction(
    val source: PathNode, val target: PathNode,
    val overwrite: Boolean = false,
    val copyAttributes: Boolean = false,
    val followLinks: Boolean = false,
    override val onError: ErrorHandler = ::skipOnError
) : FilesystemAction {
    init {
        if (source.path.fileSystem != target.path.fileSystem)
            throw ProviderMismatchException("The source and target paths must belong to the same filesystem.")
    }

    override fun applyView(viewNode: MutablePathNode) {
        addNodeToView(viewNode, target)
    }

    override fun applyFilesystem() {
        copyRecursively(
            source.path, target.path,
            overwrite = overwrite, copyAttributes = copyAttributes, followLinks = followLinks, onError = onError
        )
    }
}

/**
 * An action that creates the file represented by [target].
 *
 * The following exceptions can be passed to [onError]:
 * - [IOException]: Some problem occurred while creating the file.
 *
 * @property [target] The path node representing the file to create.
 * @property [recursive] Create this file and all its descendants.
 */
data class CreateAction(
    val target: PathNode,
    val recursive: Boolean = false,
    override val onError: ErrorHandler = ::skipOnError
) : FilesystemAction {
    override fun applyView(viewNode: MutablePathNode) {
        addNodeToView(viewNode, target)
    }

    override fun applyFilesystem() {
        target.createFile(recursive = recursive, onError = onError)
    }
}

/**
 * An action that recursively deletes a file or directory represented by [target].
 *
 * This operation is not atomic. Deleting an individual file or directory may not be atomic either.
 *
 * If the file to be deleted is a symbolic link then the link itself, and not its target, is deleted.
 *
 * The following exceptions can be passed to [onError]:
 * - [NoSuchFileException]: There was an attempt to delete a nonexistent file.
 * - [AccessDeniedException]: There was an attempt to open a directory that didn't succeed.
 * - [FileSystemLoopException]: [followLinks] is `true` and a cycle of symbolic links was detected.
 * - [IOException]: Some other problem occurred while deleting.
 *
 * @property [target] A path node representing the file or directory to delete.
 * @property [followLinks] Follow symbolic links when walking the directory tree.
 */
data class DeleteAction(
    val target: PathNode,
    val followLinks: Boolean = false,
    override val onError: ErrorHandler = ::skipOnError
) : FilesystemAction {
    override fun applyView(viewNode: MutablePathNode) {
        removeNodeFromView(viewNode, target)
    }

    override fun applyFilesystem() {
        deleteRecursively(target.path, followLinks = followLinks, onError = onError)
    }
}