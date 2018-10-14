package diffir

import java.io.File

/**
 * A class that identifies duplicate files.
 *
 * Files are considered to be duplicates if they have the same size and checksum.
 *
 * @property [dirPath] The path of the directory to search for duplicate files.
 */
class DuplicateFileFinder(val dirPath: File) {
    /**
     * A map that maps each file path to a set of duplicates.
     *
     * Each key is included in its own set of duplicates. To populate this map, call the [find] method.
     */
    var duplicates: Map<FilePath, Set<FilePath>> = mapOf()
        private set

    /**
     * Finds duplicate files in [dirPath] and populates [duplicates].
     */
    fun find() {
        // Groups files based on their size.
        val fileSizes = mutableMapOf<Long, MutableSet<File>>()
        for (file in dirPath.walkTopDown()) {
            if (!file.isFile) continue

            fileSizes
                .getOrPut(file.length()) { mutableSetOf() }
                .add(file)
        }

        // Group files that are identical.
        val fileGroups = mutableSetOf<MutableSet<File>>()
        val fileChecksums = mutableMapOf<ByteArray, MutableSet<File>>()
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
            fileGroups.addAll(fileChecksums.map { it.value })
        }

        // Construct a map from the groups of identical files.
        val mutableDuplicates = mutableMapOf<FilePath, Set<FilePath>>()
        for (group in fileGroups) {
            val pathGroup = group.asSequence().map { MutableFilePath(it.toPath()) }.toSet()
            for (filePath in pathGroup) {
                mutableDuplicates[filePath] = pathGroup
            }
        }

        // Assign the property.
        duplicates = mutableDuplicates
    }
}