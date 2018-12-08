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
import io.github.lostatc.doppel.handlers.PathConverter
import io.github.lostatc.doppel.handlers.neverConvert
import io.github.lostatc.doppel.handlers.throwOnError
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * A pair of file paths.
 *
 * @property [left] The first path.
 * @property [right] The second path.
 */
data class PathPair(val left: Path?, val right: Path?)

/**
 * An immutable comparison of two path nodes.
 *
 * Unless otherwise specified, the paths in these properties are associated with the filesystem of [left].
 *
 * @property [left] The first path node to compare.
 *
 * @property [right] The second path node to compare.
 *
 * @property [common] The relative paths of descendants that exist in both [left] and [right].
 *
 * @property [leftOnly] The relative paths of descendants that exist in [left] but not [right].
 *
 * @property [rightOnly] The relative paths of descendants that exist in [right] but not [left].
 *
 * @property [same] The relative paths of descendants that are the same in both [left] and [right]. Files are the same
 * if they have the same relative path and the same contents. How file contents are compared is determined by the
 * [type][PathNode.type].
 *
 * @property [different] The relative paths of descendants that are different in both [left] and [right]. Files are
 * different if they have the same relative path and different contents. How file contents are compared is determined by
 * the [type][PathNode.type].
 *
 * @property [leftNewer] The relative paths of descendants that were modified more recently in [left] than in [right].
 *
 * @property [rightNewer] The relative paths of descendants that were modified more recently in [right] than in [left].
 *
 * @property [pairs] Pairs of absolute paths of descendants. If a path exists in one node but not the other, the missing
 * path will be `null`. The left path is associated with the filesystem of [left], and the right path is associated with
 * the filesystem of [right].
 */
data class PathDiff(
    val left: PathNode, val right: PathNode,
    val common: Set<Path>,
    val leftOnly: Set<Path>, val rightOnly: Set<Path>,
    val same: Set<Path>, val different: Set<Path>,
    val leftNewer: Set<Path>, val rightNewer: Set<Path>,
    val pairs: Set<PathPair>
) {
    companion object {
        /**
         * Constructs a new [PathDiff] from a [left] and [right] path node.
         *
         * If [left] and [right] are associated with different file systems, [pathConverter] will be invoked to convert
         * them.
         *
         * The following exceptions can be passed to [onError]:
         * - [InvalidPathException]: A path could not be converted by [pathConverter].
         * - [NoSuchFileException] A descendant of one of the path nodes was not found in the file system.
         * - [IOException]: Some other I/O error occurred.
         *
         * @param [onError] A function that is called for each error that occurs and determines how to handle them.
         * @property [pathConverter] A function which is used to convert [Path] objects between file systems.
         */
        fun fromNodes(
            left: PathNode, right: PathNode,
            onError: ErrorHandler = ::throwOnError,
            pathConverter: PathConverter = ::neverConvert
        ): PathDiff {
            // Convert the paths of the descendants of right node to the file system of the left node.
            val convertedPaths = mutableMapOf<Path, Path>()
            associate@ for (descendant in right.relativeDescendants.keys) {
                try {
                    convertedPaths[pathConverter(descendant, left.path.fileSystem)] = descendant
                } catch (e: InvalidPathException) {
                    when (onError(descendant, e)) {
                        ErrorHandlerAction.SKIP -> continue@associate
                        ErrorHandlerAction.TERMINATE -> break@associate
                    }
                }
            }

            val leftDescendants = left.relativeDescendants.keys
            val rightDescendants = convertedPaths.keys

            // Compare file paths.
            val common = leftDescendants intersect rightDescendants
            val leftOnly = leftDescendants - rightDescendants
            val rightOnly = rightDescendants - leftDescendants

            // Create absolute path pairs.
            val pairs = (leftDescendants union rightDescendants).map {
                val leftDescendant = left.relativeDescendants[it]?.path
                val rightDescendant = right.relativeDescendants[convertedPaths[it]]?.path

                PathPair(leftDescendant, rightDescendant)
            }.toSet()

            // Compare files in the file system.
            val same = mutableSetOf<Path>()
            val different = mutableSetOf<Path>()
            val leftNewer = mutableSetOf<Path>()
            val rightNewer = mutableSetOf<Path>()

            compare@ for (commonPath in common) {
                val leftNode = left.relativeDescendants[commonPath] ?: continue
                val rightNode = right.relativeDescendants[convertedPaths[commonPath]] ?: continue

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
                leftNewer = leftNewer, rightNewer = rightNewer,
                pairs = pairs
            )
        }
    }
}