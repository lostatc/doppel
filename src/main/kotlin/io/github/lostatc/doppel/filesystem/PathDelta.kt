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
import io.github.lostatc.doppel.path.PathNode
import java.util.Deque
import java.util.LinkedList
import java.util.Objects

/**
 * A set of changes to apply to the file system.
 *
 * This class allows for creating a set of changes that can be applied to the file system. Changes are stored in a queue
 * and can be applied to the file system all at once. New changes can be queued up by passing instances of
 * [FileSystemAction] to the [add] method, but the file system is not modified until [apply] is called. There are actions
 * for moving, copying, creating and deleting files and directories. Custom actions can be created by implementing
 * [FileSystemAction].
 *
 * The [view] method can be used to see what a directory will look like after all changes are applied. The [clear] and
 * [undo] methods can be used to undo changes before they're applied.
 */
class PathDelta {
    /**
     * A queue of actions to apply to the file system.
     */
    private val actions: Deque<FileSystemAction> = LinkedList()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PathDelta) return false
        return actions == other.actions
    }

    override fun hashCode(): Int = Objects.hash(actions)

    /**
     * Returns a delta containing all the changes in this delta first and [other] second.
     */
    operator fun plus(other: PathDelta): PathDelta {
        val newDelta = PathDelta()
        newDelta.add(*actions.toTypedArray())
        newDelta.add(*other.actions.toTypedArray())
        return newDelta
    }

    /**
     * Adds the given [changes] to the queue.
     *
     * These changes are not applied to the file system until the [apply] method is called.
     */
    fun add(vararg changes: FileSystemAction) {
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
        require(numChanges >= 0) { "the given number of changes must be positive" }

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
     * Shows what the file system will look like after all changes are applied.
     *
     * This returns a deep copy of [viewNode] that represents what the file system will look like after [apply] is called
     * assuming there are no errors. This method does not modify the file system.
     */
    fun view(viewNode: PathNode): PathNode {
        val outputNode = viewNode.toMutablePathNode()
        for (action in actions) {
            action.applyView(outputNode)
        }
        return outputNode
    }

    /**
     * Applies the changes in the queue to the file system in the order they were made.
     *
     * Applying the changes does not consume them. If an [ErrorHandler] that was passed to a [FileSystemAction] instance
     * throws an exception, it will be thrown here.
     */
    fun apply() {
        for (action in actions) {
            action.applyFileSystem()
        }
    }
}
