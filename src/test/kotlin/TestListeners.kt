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

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import io.kotlintest.Description
import io.kotlintest.Spec
import io.kotlintest.extensions.TestListener
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path

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
        val filesystem = Jimfs.newFileSystem(Configuration.unix())

        existingFile = filesystem.getPath("/", "existingFile")
        existingDir = filesystem.getPath("/", "existingDir")
        nonexistentFile = filesystem.getPath("/", "nonexistent")

        Files.createFile(existingFile)
        Files.createDirectories(existingDir)
    }
}

/**
 * A test listener that creates a new in-memory filesystem for each test.
 */
class FilesystemListener : TestListener {
    lateinit var filesystem: FileSystem

    override fun beforeTest(description: Description) {
        filesystem = Jimfs.newFileSystem(Configuration.unix())
    }
}