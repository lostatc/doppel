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

import com.google.common.jimfs.Jimfs
import io.github.lostatc.doppel.testing.DEFAULT_JIMFS_CONFIG
import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec
import java.nio.file.Files

class DuplicateFileFinderTest : WordSpec() {
    init {
        "DuplicateFileFinder.find" should {
            "identify identical files" {
                val fs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)

                val root = fs.getPath("root")
                val duplicateA = fs.getPath("root", "duplicateA")
                val duplicateB = fs.getPath("root", "duplicateB")
                val uniqueA = fs.getPath("root", "uniqueA")
                val uniqueB = fs.getPath("root", "uniqueB")

                Files.createDirectories(root)
                Files.createFile(duplicateA)
                Files.createFile(duplicateB)
                Files.createFile(uniqueA)
                Files.createFile(uniqueB)

                Files.write(duplicateA, listOf("abc"))
                Files.write(duplicateB, listOf("abc"))
                Files.write(uniqueA, listOf("aaa"))
                Files.write(uniqueB, listOf("bbb"))

                val expected = mapOf(
                    duplicateA to setOf(duplicateA, duplicateB),
                    duplicateB to setOf(duplicateA, duplicateB),
                    uniqueA to setOf(uniqueA),
                    uniqueB to setOf(uniqueB)
                )

                val duplicateFinder = DuplicateFileFinder(root)
                duplicateFinder.find()

                duplicateFinder.duplicates.shouldBe(expected)
            }
        }
    }
}