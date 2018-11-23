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
import io.github.lostatc.doppel.path.PathNode
import java.nio.file.Path
import java.nio.file.ProviderMismatchException
import java.util.Deque
import java.util.LinkedList
import java.util.Objects

/**
 * A set of changes to apply to the filesystem.
 *
 * This class allows for creating a set of changes that can be applied to multiple directories. Changes are stored in a
 * queue and can be applied to the filesystem all at once. New changes can be queued up by passing instances of
 * [FilesystemAction] to the [add] method, but the filesystem is not modified until [apply] is called. There are actions
 * for moving, copying, creating and deleting files and directories. Custom actions can be created by implementing
 * [FilesystemAction].
 *
 * [FilesystemAction] classes accept both absolute and relative path nodes. If the paths are relative, they are resolved
 * against the path provided to [apply]. Using relative paths allows you to apply the changes to multiple directories.
 *
 * The [view] method can be used to see what a directory will look like after all changes are applied. The [clear] and
 * [undo] methods can be used to undo changes before they're applied.
 */
class PathDelta {
    /**
     * A list of actions to apply to the filesystem.
     */
    private val actions: Deque<FilesystemAction> = LinkedList()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PathDelta) return false
        return actions == other.actions
    }

    override fun hashCode(): Int = Objects.hash(actions)

    /**
     * Adds the given [changes] to the queue.
     *
     * These changes are not applied to the filesystem until the [apply] method is called.
     */
    fun add(vararg changes: FilesystemAction) {
        for (change in changes) {
            actions.addLast(change)
        }
    }

    /**
     * Removes the last [numChanges] changes from the queue and returns how many were removed.
     *
     * @throws [IllegalArgumentException] The given [numChanges] is negative.
     */
    fun undo(numChanges: Int): Int {
        require(numChanges > 0) { "the given number of changes must be positive" }

        val startSize = actions.size
        repeat(numChanges) { actions.pollLast() }
        return startSize - actions.size
    }

    /**
     * Removes all changes from the queue and returns how many were removed.
     */
    fun clear(): Int {
        val numActions = actions.size
        actions.clear()
        return numActions
    }

    /**
     * Returns what [dirNode] will look like after the [apply] method is called assuming there are no errors.
     *
     * Any relative paths that were passed to [FilesystemAction] classes are resolved against [dirNode] if they belong
     * to the same filesystem as it. This method does not modify the filesystem.
     */
    fun view(dirNode: PathNode): PathNode {
        val outputPath = dirNode.toMutablePathNode()
        for (action in actions) {
            action.applyView(outputPath)
        }
        return outputPath
    }

    /**
     * Applies the changes in the queue to the filesystem in the order they were made.
     *
     * Applying the changes does not consume them. If [dirPath] is not `null`, any relative paths that were passed to
     * [FilesystemAction] instances are resolved against it. If an [ErrorHandler] that was passed to a
     * [FilesystemAction] instance throws an exception, it will be thrown here.
     *
     * @throws [ProviderMismatchException] The given [dirPath] doesn't belong to the same filesystem as the paths passed
     * to one of the [FilesystemAction] instances.
     */
    fun apply(dirPath: Path? = null) {
        for (action in actions) {
            action.applyFilesystem(dirPath)
        }
    }
}
