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

import com.google.common.jimfs.Jimfs
import io.github.lostatc.doppel.testing.DEFAULT_JIMFS_CONFIG
import io.kotlintest.assertSoftly
import io.kotlintest.matchers.collections.shouldContainExactly
import io.kotlintest.specs.WordSpec
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.FileTime

class PathDiffTest : WordSpec() {
    init {
        "PathDiff.fromPathNodes" should {
            "correctly compare paths" {
                val leftNode = PathNode.of("left") {
                    file("b")
                    dir("c") {
                        file("d")
                    }
                }
                val rightNode = PathNode.of("right") {
                    file("b")
                    dir("c") {
                        file("e")
                    }
                }

                val diff = leftNode.diff(rightNode)

                assertSoftly {
                    diff.common.shouldContainExactly(Paths.get("b"), Paths.get("c"))
                    diff.leftOnly.shouldContainExactly(Paths.get("c", "d"))
                    diff.rightOnly.shouldContainExactly(Paths.get("c", "e"))
                }
            }

            "correctly compare file contents" {
                val fs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)

                val leftNode = PathNode.of(fs.getPath("left")) {
                    file("same")
                    file("different")
                }
                val rightNode = PathNode.of(fs.getPath("right")) {
                    file("same")
                    file("different")
                }

                leftNode.createFile(recursive = true)
                rightNode.createFile(recursive = true)

                Files.write(fs.getPath("left", "same"), listOf("same"))
                Files.write(fs.getPath("right", "same"), listOf("same"))
                Files.write(fs.getPath("left", "different"), listOf("left"))
                Files.write(fs.getPath("right", "different"), listOf("right"))

                val diff = leftNode.diff(rightNode)

                assertSoftly {
                    diff.same.shouldContainExactly(fs.getPath("same"))
                    diff.different.shouldContainExactly(fs.getPath("different"))
                }
            }

            "correctly compare file times" {
                val fs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)

                val leftNode = PathNode.of(fs.getPath("left")) {
                    file("leftNewer")
                    file("rightNewer")
                }
                val rightNode = PathNode.of(fs.getPath("right")) {
                    file("leftNewer")
                    file("rightNewer")
                }

                leftNode.createFile(recursive = true)
                rightNode.createFile(recursive = true)

                Files.setLastModifiedTime(fs.getPath("left", "leftNewer"), FileTime.fromMillis(100))
                Files.setLastModifiedTime(fs.getPath("left", "rightNewer"), FileTime.fromMillis(1))
                Files.setLastModifiedTime(fs.getPath("right", "leftNewer"), FileTime.fromMillis(1))
                Files.setLastModifiedTime(fs.getPath("right", "rightNewer"), FileTime.fromMillis(100))

                val diff = leftNode.diff(rightNode)

                assertSoftly {
                    diff.leftNewer.shouldContainExactly(fs.getPath("leftNewer"))
                    diff.rightNewer.shouldContainExactly(fs.getPath("rightNewer"))
                }
            }
        }
    }
}
