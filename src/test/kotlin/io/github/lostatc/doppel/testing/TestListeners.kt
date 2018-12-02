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

package io.github.lostatc.doppel.testing

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import io.kotlintest.Description
import io.kotlintest.Spec
import io.kotlintest.extensions.TestListener
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path

/**
 * The default configuration to use for in-memory file systems used for testing.
 */
val DEFAULT_JIMFS_CONFIG: Configuration = Configuration.unix().toBuilder()
    .setWorkingDirectory("/")
    .build()

/**
 * Convert a path to another file system of the same type.
 */
fun convertBasicPath(path: Path, fileSystem: FileSystem): Path = fileSystem.getPath(path.toString())

/**
 * A test listener that creates existing and a nonexistent files for testing.
 */
class NonexistentFileListener : TestListener {
    /**
     * A file that exists in the file system.
     */
    lateinit var existingFile: Path

    /**
     * A directory that exists in the file system.
     */
    lateinit var existingDir: Path

    /**
     * A file that does not exist in the file system.
     */
    lateinit var nonexistentFile: Path

    override fun beforeSpec(description: Description, spec: Spec) {
        val fs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)

        existingFile = fs.getPath("/", "existingFile")
        existingDir = fs.getPath("/", "existingDir")
        nonexistentFile = fs.getPath("/", "nonexistent")

        Files.createFile(existingFile)
        Files.createDirectories(existingDir)
    }
}
