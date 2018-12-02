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
import io.github.lostatc.doppel.testing.NonexistentFileListener
import io.kotlintest.assertSoftly
import io.kotlintest.extensions.TestListener
import io.kotlintest.matchers.boolean.shouldBeFalse
import io.kotlintest.matchers.boolean.shouldBeTrue
import io.kotlintest.matchers.collections.shouldBeEmpty
import io.kotlintest.matchers.maps.shouldContain
import io.kotlintest.matchers.maps.shouldNotContainKey
import io.kotlintest.matchers.types.shouldBeInstanceOf
import io.kotlintest.matchers.types.shouldBeSameInstanceAs
import io.kotlintest.matchers.types.shouldNotBeSameInstanceAs
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.WordSpec
import java.nio.file.Paths

class MutablePathNodeTest : WordSpec() {

    val nonexistentListener: NonexistentFileListener =
        NonexistentFileListener()

    override fun listeners(): List<TestListener> = listOf(nonexistentListener)

    init {
        "MutablePathNode constructor" should {
            "throw if the given file name has a parent" {
                shouldThrow<IllegalArgumentException> {
                    MutablePathNode(Paths.get("a", "b"))
                }
            }

            "throw if the file name and parent file systems are different" {
                val nameFs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)
                val parentFs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)

                shouldThrow<IllegalArgumentException> {
                    MutablePathNode(nameFs.getPath("a"), MutablePathNode(parentFs.getPath("b")))
                }
            }
        }

        "MutablePathNode.type" should {
            "return the type passed to the constructor" {
                val testNode = PathNode.of(
                    "a",
                    type = RegularFileType()
                )
                testNode.type.shouldBeInstanceOf<RegularFileType>()
            }

            "change to a directory type when children are added" {
                val testNode = MutablePathNode.of(
                    "a",
                    type = RegularFileType()
                )
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
                PathNode.of("a", "b")
                    .shouldBe(PathNode.of("a", "b"))
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

        "MutablePathNode.resolve" should {
            "return the given path if it is absolute" {
                val testNode = PathNode.of("/", "a")
                val otherNode = PathNode.of("/", "b")

                testNode.resolve(otherNode).shouldBe(otherNode)
            }

            "resolve the given path against an absolute path" {
                val testNode = PathNode.of("/", "a")
                val otherNode = PathNode.of("b", "c")

                testNode.resolve(otherNode).shouldBe(
                    PathNode.of(
                        "/",
                        "a",
                        "b",
                        "c"
                    )
                )
            }

            "resolve the given path against a relative path" {
                val testNode = PathNode.of("a")
                val otherNode = PathNode.of("b", "c")

                testNode.resolve(otherNode).shouldBe(PathNode.of("a", "b", "c"))
            }
        }

        "MutablePathNode.toAbsoluteNode" should {
            "return a copy of the node if it is absolute" {
                val testNode = PathNode.of("/", "a", "b")
                val absoluteTestNode = testNode.toAbsoluteNode()

                testNode.shouldBe(absoluteTestNode)
                testNode.shouldNotBeSameInstanceAs(absoluteTestNode)
            }

            "return an absolute copy of the node" {
                val fs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)
                val testNode = PathNode.of(fs.getPath("a", "b"))
                val expectedNode = PathNode.of(fs.getPath("/", "a", "b"))

                testNode.toAbsoluteNode().shouldBe(expectedNode)
            }
        }

        "MutablePathNode.exists" should {
            "identify that an existing file exists" {
                val testNode = PathNode.of(
                    nonexistentListener.existingFile,
                    RegularFileType()
                )
                testNode.exists(checkType = false).shouldBeTrue()
                testNode.exists(checkType = true).shouldBeTrue()
            }

            "identify that a file is a different type" {
                val testNode = PathNode.of(
                    nonexistentListener.existingDir,
                    RegularFileType()
                )
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
            "return false if the nodes have different types" {
                val thisNode = PathNode.of(
                    "a",
                    type = RegularFileType()
                )
                val otherNode = PathNode.of(
                    "b",
                    type = DirectoryType()
                )

                thisNode.sameContentsAs(otherNode).shouldBeFalse()
            }
        }

        "MutablePathNode.createFile" should {
            "create a single file" {
                val fs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)
                val testNode = PathNode.of(
                    fs.getPath("a"),
                    type = RegularFileType()
                )
                testNode.createFile()

                testNode.exists().shouldBeTrue()
            }

            "recursively create files" {
                val fs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)
                val testNode = PathNode.of(fs.getPath("a")) {
                    file("b")
                    dir("c") {
                        file("d")
                    }
                }
                testNode.createFile(recursive = true)

                testNode.exists(recursive = true).shouldBeTrue()
            }
        }

        "MutablePathNode.addDescendant" should {
            "throw if the given node does not start with this node" {
                val thisNode = MutablePathNode.of("a", "b")
                val otherNode = MutablePathNode.of("a", "c")

                shouldThrow<IllegalArgumentException> {
                    thisNode.addDescendant(otherNode)
                }
            }

            "throw if the given node has the same path as this node" {
                val thisNode = MutablePathNode.of("/", "a")
                val otherNode = MutablePathNode.of("/", "a")

                shouldThrow<IllegalArgumentException> {
                    thisNode.addDescendant(otherNode)
                }
            }

            "add an immediate child of this node" {
                val thisNode = MutablePathNode.of("/")
                val otherNode = MutablePathNode.of("/", "a")

                val expectedNode = PathNode.of("/", "a")

                thisNode.addDescendant(otherNode)
                thisNode.children.shouldContain(expectedNode.fileName, expectedNode)
            }

            "add a descendant of this node" {
                val thisNode = MutablePathNode.of("/") {
                    dir("a") {
                        file("b")
                    }
                }
                val otherNode = MutablePathNode.of("/", "a", "c", "d")

                val expectedNode = PathNode.of("/", "a", "c", "d")

                thisNode.addDescendant(otherNode)
                thisNode.descendants.shouldContain(expectedNode.path, expectedNode)
            }

            "replace an existing node" {
                val thisNode = MutablePathNode.of("/") {
                    file("a")
                }
                val otherNode = MutablePathNode.of("/", "a", type = DirectoryType())

                val expectedNode = PathNode.of("/", "a", type = DirectoryType())

                thisNode.addDescendant(otherNode)
                thisNode.children.shouldContain(expectedNode.fileName, expectedNode)
            }

            "not copy the given node" {
                val thisNode = MutablePathNode.of("/")
                val otherNode = MutablePathNode.of("/", "a")

                thisNode.addDescendant(otherNode)
                thisNode.children[otherNode.fileName].shouldBeSameInstanceAs(otherNode)
            }
        }

        "MutablePathNode.addRelativeDescendant" should {
            "throw if the given node is absolute" {
                val thisNode = MutablePathNode.of("a")
                val otherNode = MutablePathNode.of("/", "a", "b")

                shouldThrow<IllegalArgumentException> {
                    thisNode.addRelativeDescendant(otherNode)
                }
            }

            "add an immediate child of this node" {
                val thisNode = MutablePathNode.of("/")
                val otherNode = MutablePathNode.of("a", "b")

                val expectedNode = PathNode.of("/", "a", "b")

                thisNode.addRelativeDescendant(otherNode)
                thisNode.descendants.shouldContain(expectedNode.path, expectedNode)
            }

            "add a node with a single segment" {
                val thisNode = MutablePathNode.of("/")
                val otherNode = MutablePathNode.of("a")

                val expectedNode = PathNode.of("/", "a")

                thisNode.addRelativeDescendant(otherNode)
                thisNode.descendants.shouldContain(expectedNode.path, expectedNode)
            }

            "add a descendant of this node" {
                val thisNode = MutablePathNode.of("/") {
                    dir("a") {
                        file("b")
                    }
                }
                val otherNode = MutablePathNode.of("a", "c", "d")

                val expectedNode = PathNode.of("/", "a", "c", "d")

                thisNode.addRelativeDescendant(otherNode)
                thisNode.descendants.shouldContain(expectedNode.path, expectedNode)
            }

            "replace an existing node" {
                val thisNode = MutablePathNode.of("/") {
                    file("a")
                }
                val otherNode = MutablePathNode.of("a", type = DirectoryType())

                val expectedNode = PathNode.of("/", "a", type = DirectoryType())

                thisNode.addRelativeDescendant(otherNode)
                thisNode.children.shouldContain(expectedNode.fileName, expectedNode)
            }

            "not copy the given node" {
                val thisNode = MutablePathNode.of("/")
                val otherNode = MutablePathNode.of("a")

                thisNode.addRelativeDescendant(otherNode)
                thisNode.children[otherNode.fileName].shouldBeSameInstanceAs(otherNode)
            }
        }

        "MutablePathNode.removeDescendant" should {
            "remove the descendant from the tree" {
                val thisNode = MutablePathNode.of("/") {
                    dir("a") {
                        file("b")
                    }
                }
                val otherPath = Paths.get("/", "a", "b")
                val otherNode = thisNode.removeDescendant(otherPath)

                assertSoftly {
                    thisNode.descendants.shouldNotContainKey(otherPath)
                    otherNode?.fileName.shouldBe(otherPath.fileName)
                }
            }

            "return null if the descendant doesn't exist." {
                val thisNode = MutablePathNode.of("/") {
                    file("a")
                }
                val otherPath = Paths.get("/", "a", "b")

                val otherNode = thisNode.removeDescendant(otherPath)
                otherNode.shouldBe(null)
            }
        }

        "MutablePathNode.removeRelativeDescendant" should {
            "remove the descendant from the tree" {
                val thisNode = MutablePathNode.of("/") {
                    dir("a") {
                        file("b")
                    }
                }
                val otherPath = Paths.get("a", "b")
                val otherNode = thisNode.removeRelativeDescendant(otherPath)

                assertSoftly {
                    thisNode.relativeDescendants.shouldNotContainKey(otherPath)
                    otherNode?.fileName.shouldBe(otherPath.fileName)
                }
            }

            "return null if the descendant doesn't exist." {
                val thisNode = MutablePathNode.of("/") {
                    file("a")
                }
                val otherPath = Paths.get("a", "b")

                val otherNode = thisNode.removeRelativeDescendant(otherPath)
                otherNode.shouldBe(null)
            }
        }

        "MutablePathNode.clearChildren" should {
            "remove all children from the node" {
                val testNode = MutablePathNode.of("/") {
                    file("a")
                    dir("b") {
                        file("c")
                    }
                }
                testNode.clearChildren()

                testNode.children.entries.shouldBeEmpty()
            }
        }
    }
}
