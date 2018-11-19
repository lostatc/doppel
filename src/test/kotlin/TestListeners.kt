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

package doppel.listeners

import doppel.path.MutablePathNode
import doppel.path.PathNode
import doppel.path.WalkDirection
import doppel.path.dir
import doppel.path.file
import io.kotlintest.Description
import io.kotlintest.Spec
import io.kotlintest.TestResult
import io.kotlintest.extensions.TestListener
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * A test listener that creates existing and a nonexistent files for testing.
 */
class NonexistentFileListener : TestListener {
    /**
     * A file that exists in the filesystem.
     */
    lateinit var existingFile: Path

    /**
     * A directory that exists in the filesystem.
     */
    lateinit var existingDir: Path

    /**
     * A file that does not exist in the filesystem.
     */
    lateinit var nonexistentFile: Path

    override fun beforeSpec(description: Description, spec: Spec) {
        existingFile = Files.createTempFile("", ".tmp")
        existingDir = Files.createTempDirectory("")
        nonexistentFile = Paths.get("/", "nonexistent")
    }

    override fun afterSpec(description: Description, spec: Spec) {
        Files.deleteIfExists(existingFile)
        Files.deleteIfExists(existingDir)
    }
}

/**
 * A test listener that creates a tree of files and directories in the filesystem.
 */
class FileTreeListener : TestListener {
    /**
     * A directory path representing the tree of files and directories.
     */
    lateinit var pathNode: PathNode

    override fun beforeTest(description: Description) {
        val tempPath = Files.createTempDirectory("")
        pathNode = MutablePathNode.of(tempPath) {
            file("b")
            dir("c", "d") {
                file("e")
                dir("f")
            }
        }

        pathNode.createFile(recursive = true)
    }

    override fun afterTest(description: Description, result: TestResult) {
        for (descendant in pathNode.walkChildren(WalkDirection.BOTTOM_UP)) {
            Files.deleteIfExists(descendant.path)
        }
        Files.deleteIfExists(pathNode.path)
    }
}