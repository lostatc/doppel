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

import doppel.path.DirectoryType
import doppel.path.PathNode
import doppel.path.RegularFileType
import doppel.path.dir
import doppel.path.file
import io.kotlintest.matchers.maps.shouldContain
import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec
import java.nio.file.Paths

class PathNodeFactory : WordSpec() {
    init {
        "MutablePathNode.of" should {
            "make the last segment the file name" {
                val testPath = Paths.get("parent", "fileName")
                PathNode.of(testPath).fileName.shouldBe(Paths.get("fileName"))
            }

            "make the second-last segment the parent" {
                val testPath = Paths.get("parent", "fileName")
                PathNode.of(testPath).parent?.fileName.shouldBe(Paths.get("parent"))
            }

            "not create a parent from a single segment" {
                val testPath = Paths.get("fileName")
                PathNode.of(testPath).parent.shouldBe(null)
            }

            "make the path a child of its parent" {
                val testPath = Paths.get("parent", "fileName")
                val testNode = PathNode.of(testPath)
                testNode.parent?.children?.shouldContain(testNode.fileName, testNode)
            }

            "properly create a node from segments" {
                val testNode = PathNode.of("/", "a", "b", "c")
                testNode.path.shouldBe(Paths.get("/", "a", "b", "c"))
            }

            "add children to the node" {
                val testNode = PathNode.of(Paths.get("/", "a")) {
                    file("b")
                    dir("c")
                }

                val children = mapOf(
                    Paths.get("b") to PathNode.of("/", "a", "b", type = RegularFileType()),
                    Paths.get("c") to PathNode.of("/", "a", "c", type = DirectoryType())
                )

                testNode.children.shouldBe(children)
            }
        }

        "MutablePathNode.fromFilesystem" should {
            // TODO: Implement tests
        }
    }
}
