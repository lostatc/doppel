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
import doppel.path.MutablePathNode
import doppel.path.PathNode
import doppel.path.RegularFileType
import doppel.path.WalkDirection
import doppel.path.dir
import doppel.path.file
import io.kotlintest.extensions.TestListener
import io.kotlintest.matchers.boolean.shouldBeFalse
import io.kotlintest.matchers.boolean.shouldBeTrue
import io.kotlintest.matchers.collections.shouldBeEmpty
import io.kotlintest.matchers.types.shouldBeInstanceOf
import io.kotlintest.matchers.types.shouldNotBeSameInstanceAs
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.WordSpec
import java.nio.file.Paths

class MutablePathNodeTest : WordSpec() {

    val nonexistentListener: NonexistentFileListener = NonexistentFileListener()

    override fun listeners(): List<TestListener> = listOf(nonexistentListener)

    init {
        "MutablePathNode constructor" should {
            "throw if the given file name has a parent" {
                shouldThrow<IllegalArgumentException> {
                    MutablePathNode(Paths.get("a", "b"))
                }
            }

            "throw if the file name and parent filesystems are different" {
                // TODO: Implement test
            }
        }

        "MutablePathNode.type" should {
            "return the type passed to the constructor" {
                val testNode = PathNode.of("a", type = RegularFileType())
                testNode.type.shouldBeInstanceOf<RegularFileType>()
            }

            "change to a directory type when children are added" {
                val testNode = MutablePathNode.of("a", type = RegularFileType())
                val descendantNode = MutablePathNode.of("a", "b")
                testNode.addDescendant(descendantNode)
                testNode.type.shouldBeInstanceOf<DirectoryType>()
            }
        }

        "MutablePathNode.root" should {
            "return the root node for absolute paths" {
                val testPath = Paths.get("/", "a", "b")
                PathNode.of(testPath).root.path.shouldBe(testPath.root)
            }

            "return the root node for relative paths" {
                val testPath = Paths.get("a", "b", "c")
                PathNode.of(testPath).root.path.shouldBe(Paths.get("a"))
            }
        }

        "MutablePathNode.path" should {
            "work for multiple segments" {
                PathNode.of("/", "a", "b").path.shouldBe(Paths.get("/", "a", "b"))
            }

            "work for a single segment" {
                PathNode.of("a").path.shouldBe(Paths.get("a"))
            }

            "work for a root component" {
                PathNode.of("/").path.shouldBe(Paths.get("/"))
            }
        }

        "MutablePathNode.descendants" should {
            "use full paths as keys" {
                val testNode = PathNode.of("/", "a") {
                    dir("b") {
                        file("c")
                    }
                    file("d")
                }

                val expectedKeys = setOf(
                    Paths.get("/", "a", "b"),
                    Paths.get("/", "a", "b", "c"),
                    Paths.get("/", "a", "d")
                )

                testNode.descendants.keys.shouldBe(expectedKeys)
            }

            "map paths to the correct nodes" {
                val testNode = PathNode.of("/", "a") {
                    dir("b") {
                        file("c")
                    }
                    file("d")
                }

                val expectedPaths = testNode.descendants.values.map { it.path }.toSet()
                testNode.descendants.keys.shouldBe(expectedPaths)
            }
        }

        "MutablePathNode.relativeDescendants" should {
            "use relative paths as keys" {
                val testNode = PathNode.of("/", "a") {
                    dir("b") {
                        file("c")
                    }
                    file("d")
                }

                val expectedKeys = setOf(Paths.get("b"), Paths.get("b", "c"), Paths.get("d"))

                testNode.relativeDescendants.keys.shouldBe(expectedKeys)
            }

            "map paths to the correct nodes" {
                val testNode = PathNode.of("/", "a") {
                    dir("b") {
                        file("c")
                    }
                    file("d")
                }

                val expectedPaths = testNode.descendants.values.map { it.path }.toSet()
                testNode.descendants.keys.shouldBe(expectedPaths)
            }
        }

        "MutablePathNode.toString" should {
            "return a string representation" {
                PathNode.of("/", "a", "b").toString().shouldBe("/a/b")
            }
        }

        "MutablePathNode.equals" should {
            "consider mutable and immutable path nodes equal" {
                val testNode = MutablePathNode.of("a")
                testNode.shouldBe(testNode as PathNode)
            }

            "consider different instances equal" {
                PathNode.of("a", "b").shouldBe(PathNode.of("a", "b"))
            }
        }

        "MutablePathNode.hashCode" should {
            "return the same hash code for equal instances" {
                val thisNode = PathNode.of("a", "b")
                val otherNode = PathNode.of("a", "b")
                thisNode.hashCode().shouldBe(otherNode.hashCode())
            }
        }

        "MutablePathNode.toPathNode" should {
            "return a copy that is equal to the original" {
                val testNode = PathNode.of("/", "a") {
                    dir("b")
                    file("c")
                }

                testNode.toPathNode().shouldBe(testNode)
            }

            "copy the parent" {
                val testNode = PathNode.of("/", "a", "b")

                testNode.toPathNode().parent.shouldNotBeSameInstanceAs(testNode.parent)
            }
        }

        "MutablePathNode.toMutablePathNode" should {
            "return a copy that is equal to the original" {
                val testNode = PathNode.of("/", "a") {
                    dir("b")
                    file("c")
                }

                testNode.toMutablePathNode().shouldBe(testNode)
            }

            "copy the parent" {
                val testNode = PathNode.of("/", "a", "b")

                testNode.toMutablePathNode().parent.shouldNotBeSameInstanceAs(testNode.parent)
            }
        }

        "MutablePathNode.walkAncestors" should {
            "return all ancestors" {
                val testNode = PathNode.of("/", "a", "b")
                val ancestors = setOf(Paths.get("/", "a"), Paths.get("/"))
                testNode.walkAncestors().map { it.path }.toSet().shouldBe(ancestors)
            }

            "iterate in the correct order" {
                val testNode = PathNode.of("/", "a", "b")
                val expectedBottomUp = listOf(Paths.get("/", "a"), Paths.get("/"))
                val expectedTopDown = expectedBottomUp.reversed()

                testNode.walkAncestors(WalkDirection.BOTTOM_UP).map { it.path }.toList().shouldBe(expectedBottomUp)
                testNode.walkAncestors(WalkDirection.TOP_DOWN).map { it.path }.toList().shouldBe(expectedTopDown)
            }

            "return an empty list when there are no ancestors" {
                PathNode.of("a").walkAncestors().toList().shouldBeEmpty()
            }
        }

        "MutablePathNode.walkChildren" should {
            "iterate in the correct order" {
                val testNode = PathNode.of(Paths.get("/", "a")) {
                    dir("b") {
                        dir("c") {
                            file("d")
                        }
                    }
                }

                val expectedTopDown = listOf(
                    Paths.get("/", "a", "b"),
                    Paths.get("/", "a", "b", "c"),
                    Paths.get("/", "a", "b", "c", "d")
                )
                val expectedBottomUp = expectedTopDown.reversed()

                testNode.walkChildren(WalkDirection.TOP_DOWN).map { it.path }.toList().shouldBe(expectedTopDown)
                testNode.walkChildren(WalkDirection.BOTTOM_UP).map { it.path }.toList().shouldBe(expectedBottomUp)
            }

            "return an empty list when there are no children" {
                PathNode.of("a").walkChildren().toList().shouldBeEmpty()
            }
        }

        "MutablePathNode.relativize" should {
            "throw if the given path does not start with this path" {
                val testNode = PathNode.of("/", "a", "b")
                shouldThrow<IllegalArgumentException> {
                    testNode.relativize(PathNode.of("/", "c"))
                }
            }

            "throw if one path is absolute and one is relative" {
                val testNode = PathNode.of("/", "a", "b")
                shouldThrow<IllegalArgumentException> {
                    testNode.relativize(PathNode.of("a", "b", "c"))
                }
            }

            "return a relativized path from two absolute paths" {
                val testNode = PathNode.of("/", "a")
                val otherNode = PathNode.of("/", "a", "b", "c")
                val expectedPath = Paths.get("b", "c")

                testNode.relativize(otherNode).path.shouldBe(expectedPath)
            }

            "return a relativized path from two relative paths" {
                val testNode = PathNode.of("a", "b")
                val otherNode = PathNode.of("a", "b", "c", "d")
                val expectedPath = Paths.get("c", "d")

                testNode.relativize(otherNode).path.shouldBe(expectedPath)
            }

            "return a relativized path when one is the parent of the other" {
                val testNode = PathNode.of("a", "b")
                val otherNode = PathNode.of("a", "b", "c")
                val expectedPath = Paths.get("c")

                testNode.relativize(otherNode).path.shouldBe(expectedPath)
            }
        }

        "PathNode.resolve" should {
            "return the given path if it is absolute" {
                val testNode = PathNode.of("/", "a")
                val otherNode = PathNode.of("/", "b")

                testNode.resolve(otherNode).shouldBe(otherNode)
            }

            "resolve the given path against an absolute path" {
                val testNode = PathNode.of("/", "a")
                val otherNode = PathNode.of("b", "c")

                testNode.resolve(otherNode).shouldBe(PathNode.of("/", "a", "b", "c"))
            }

            "resolve the given path against a relative path" {
                val testNode = PathNode.of("a")
                val otherNode = PathNode.of("b", "c")

                testNode.resolve(otherNode).shouldBe(PathNode.of("a", "b", "c"))
            }
        }

        "MutableDirPath.diff" should {
            // TODO: Implement tests
        }

        "MutablePathNode.exists" should {
            "identify that an existing file exists" {
                val testNode = PathNode.of(nonexistentListener.existingFile, RegularFileType())
                testNode.exists(checkType = false).shouldBeTrue()
                testNode.exists(checkType = true).shouldBeTrue()
            }

            "identify that a file is a different type" {
                val testNode = PathNode.of(nonexistentListener.existingDir, RegularFileType())
                testNode.exists(checkType = false).shouldBeTrue()
                testNode.exists(checkType = true).shouldBeFalse()
            }

            "identify that a nonexistent file doesn't exist" {
                val testNode = PathNode.of(nonexistentListener.nonexistentFile)
                testNode.exists(checkType = false).shouldBeFalse()
                testNode.exists(checkType = true).shouldBeFalse()
            }
        }

        "MutablePathNode.sameContentsAs" should {
            // TODO: Implement tests
        }

        "MutablePathNode.createFile" should {
            // TODO: Implement tests
        }

        "MutablePathNode.addDescendant" should {
            // TODO: Implement tests
        }

        "MutablePathNode.addAllDescendants" should {
            // TODO: Implement tests
        }

        "MutablePathNode.addRelativeDescendant" should {
            // TODO: Implement tests
        }

        "MutablePathNode.addAllRelativeDescendants" should {
            // TODO: Implement tests
        }

        "MutablePathNode.removeDescendant" should {
            // TODO: Implement tests
        }

        "MutablePathNode.removeAllDescendants" should {
            // TODO: Implement tests
        }

        "MutablePathNode.clearChildren" should {
            // TODO: Implement tests
        }
    }
}
