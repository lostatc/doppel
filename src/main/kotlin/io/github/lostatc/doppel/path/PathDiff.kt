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
import io.github.lostatc.doppel.error.ErrorHandlerAction
import io.github.lostatc.doppel.error.skipOnError
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * An immutable comparison of two path nodes.
 *
 * @property [left] The first path node to compare.
 * @property [right] The second path node to compare.
 * @property [common] The relative paths of files that exist in both directory trees.
 * @property [leftOnly] The relative paths of files that exist in the left tree but not the right tree.
 * @property [rightOnly] The relative paths of files that exist in the right tree but not the left tree.
 * @property [same] The relative paths of files that are the same in both directory trees. Files are the same if they
 * have the same relative path and the same contents.
 * @property [different] The relative paths of files that are different in both directory trees. Files are different if
 * they have the same relative path and different contents.
 * @property [leftNewer] The relative paths of files that were modified more recently in the left tree than in the
 * right tree.
 * @property [rightNewer] The relative paths of files that were modified more recently in the right tree than in the
 * left tree.
 *
 */
data class PathDiff(
    val left: PathNode, val right: PathNode,
    val common: Set<Path>,
    val leftOnly: Set<Path>, val rightOnly: Set<Path>,
    val same: Set<Path>, val different: Set<Path>,
    val leftNewer: Set<Path>, val rightNewer: Set<Path>
) {
    companion object {
        /**
         * Constructs a new [PathDiff] from a [left] and [right] path node.
         *
         * The following exceptions can be passed to [onError]:
         * - [NoSuchFileException] A file in one of the directories was not found in the filesystem.
         * - [IOException]: Some other I/O error occurred.
         *
         * @param [onError] A function that is called for each I/O error that occurs and determines how to handle them.
         */
        fun fromPathNodes(left: PathNode, right: PathNode, onError: ErrorHandler = ::skipOnError): PathDiff {
            val leftDescendants = left.relativize(left).descendants.keys
            val rightDescendants = right.relativize(right).descendants.keys

            // Compare file paths.
            val common = leftDescendants intersect rightDescendants
            val leftOnly = leftDescendants - rightDescendants
            val rightOnly = rightDescendants - leftDescendants

            // Compare files in the filesystem.
            val same = mutableSetOf<Path>()
            val different = mutableSetOf<Path>()
            val leftNewer = mutableSetOf<Path>()
            val rightNewer = mutableSetOf<Path>()

            compare@ for (commonPath in common) {
                val leftNode = left.relativeDescendants[commonPath] ?: continue
                val rightNode = right.relativeDescendants[commonPath] ?: continue

                try {
                    // Compare the contents of files in the directories.
                    if (leftNode.sameContentsAs(rightNode)) same.add(commonPath) else different.add(commonPath)

                    // Compare the times of files in the directories.
                    val leftTime = Files.getLastModifiedTime(leftNode.path)
                    val rightTime = Files.getLastModifiedTime(rightNode.path)
                    if (leftTime > rightTime) leftNewer.add(commonPath) else rightNewer.add(commonPath)

                } catch (e: IOException) {
                    when (onError(commonPath, e)) {
                        ErrorHandlerAction.SKIP -> continue@compare
                        ErrorHandlerAction.TERMINATE -> break@compare
                    }
                }
            }

            return PathDiff(
                left, right,
                common = common,
                leftOnly = leftOnly, rightOnly = rightOnly,
                same = same, different = different,
                leftNewer = leftNewer, rightNewer = rightNewer
            )
        }
    }
}