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

import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path
import java.util.Objects

/**
 * A class that identifies duplicate files.
 *
 * Files are considered to be duplicates if they have the same size and checksum.
 *
 * @property [dirPath] The path of the directory to search for duplicate files.
 * @property [followLinks] Follow symbolic links when walking the directory tree.
 */
class DuplicateFileFinder(val dirPath: Path, val followLinks: Boolean = false) {
    /**
     * A map that maps each file path to a set of duplicates.
     *
     * Each key is included in its own set of duplicates. To populate this map, call the [find] method.
     */
    var duplicates: Map<Path, Set<Path>> = mapOf()
        private set

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is DuplicateFileFinder) return false
        return dirPath == other.dirPath && followLinks == other.followLinks && duplicates == other.duplicates
    }

    override fun hashCode(): Int = Objects.hash(dirPath, followLinks, duplicates)

    /**
     * Finds duplicate files in [dirPath] and populates [duplicates].
     */
    fun find() {
        val walkOptions = mutableSetOf<FileVisitOption>()
        if (followLinks) walkOptions.add(FileVisitOption.FOLLOW_LINKS)

        // Group files based on their size.
        val fileSizes = mutableMapOf<Long, MutableSet<Path>>()
        for (file in Files.walk(dirPath)) {
            if (Files.isDirectory(file)) continue

            fileSizes
                .getOrPut(Files.size(file)) { mutableSetOf() }
                .add(file)
        }

        // Group files that are identical.
        val fileGroups = mutableSetOf<MutableSet<Path>>()
        val fileChecksums = mutableMapOf<ByteArray, MutableSet<Path>>()
        for ((_, group) in fileSizes) {
            if (group.size == 1) {
                // This file is unique. Add it to its own group.
                fileGroups.add(group)
                continue
            }

            // There are multiple files of the same size. Confirm they're identical by comparing checksums.
            fileChecksums.clear()
            for (file in group) {
                fileChecksums
                    .getOrPut(getFileChecksum(file)) { mutableSetOf() }
                    .add(file)
            }

            // Add each set of identical files to a group.
            fileGroups.addAll(fileChecksums.values)
        }

        // Construct a map from the groups of identical files.
        val mutableDuplicates = mutableMapOf<Path, Set<Path>>()
        for (group in fileGroups) {
            for (file in group) {
                mutableDuplicates[file] = group
            }
        }

        // Assign the property.
        duplicates = mutableDuplicates
    }
}