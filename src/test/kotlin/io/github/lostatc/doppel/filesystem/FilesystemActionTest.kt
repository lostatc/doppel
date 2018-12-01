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
import io.github.lostatc.doppel.handlers.skipOnError
import io.github.lostatc.doppel.handlers.throwOnError
import io.github.lostatc.doppel.path.MutablePathNode
import io.github.lostatc.doppel.path.PathNode
import io.github.lostatc.doppel.path.dir
import io.github.lostatc.doppel.path.file
import io.github.lostatc.doppel.path.symlink
import io.github.lostatc.doppel.testing.DEFAULT_JIMFS_CONFIG
import io.github.lostatc.doppel.testing.convertBasicPath
import io.kotlintest.assertSoftly
import io.kotlintest.matchers.boolean.shouldBeTrue
import io.kotlintest.matchers.collections.shouldBeEmpty
import io.kotlintest.matchers.maps.shouldContainKey
import io.kotlintest.matchers.maps.shouldNotContainKey
import io.kotlintest.shouldThrow
import io.kotlintest.specs.WordSpec
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.InvalidPathException

class FilesystemActionKtTest : WordSpec() {
    init {
        "addNodeToView" should {
            "add the node if it is a descendant" {
                val viewNode = MutablePathNode.of("/", "a")
                val pathNode = PathNode.of("/", "a", "b")
                addNodeToView(pathNode, viewNode)

                viewNode.descendants.shouldContainKey(pathNode.path)
            }

            "not add the node if it is not a descendant" {
                val viewNode = MutablePathNode.of("/", "a")
                val pathNode = PathNode.of("/", "c", "b")
                addNodeToView(pathNode, viewNode)

                viewNode.descendants.shouldNotContainKey(pathNode.path)
            }

            "not add the node if it belongs to a different filesystem" {
                val viewNodeFs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)
                val viewNode = MutablePathNode.of(viewNodeFs.getPath("/", "a"))

                val pathNodeFs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)
                val pathNode = PathNode.of(pathNodeFs.getPath("/", "a", "b"))

                addNodeToView(pathNode, viewNode)

                viewNode.descendants.entries.shouldBeEmpty()
            }
        }

        "removeNodeFromView" should {
            "remove the node if it is a descendant" {
                val viewNode = MutablePathNode.of("/", "a") {
                    file("b")
                }
                val pathNode = PathNode.of("/", "a", "b")
                removeNodeFromView(pathNode, viewNode)

                viewNode.descendants.shouldNotContainKey(pathNode.path)
            }

            "not remove the node if it belongs to a different filesystem" {
                val viewNodeFs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)
                val viewNode = MutablePathNode.of(viewNodeFs.getPath("/", "a"))
                val testPath = viewNodeFs.getPath("/", "a", "b")
                viewNode.addDescendant(MutablePathNode.of(testPath))

                val pathNodeFs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)
                val pathNode = PathNode.of(pathNodeFs.getPath("/", "a", "b"))

                removeNodeFromView(pathNode, viewNode)

                viewNode.descendants.shouldContainKey(testPath)
            }
        }
    }
}

class MoveActionTest : WordSpec() {
    init {
        "MoveAction.applyFilesystem" should {
            "throw if source and target belong to different filesystems" {
                val sourceFs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)
                val targetFs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)

                val sourceNode = PathNode.of(sourceFs.getPath("source")) {
                    file("a")
                }
                val targetNode = PathNode.of(targetFs.getPath("target"))
                sourceNode.createFile(recursive = true)

                val action = MoveAction(sourceNode, targetNode, onError = ::throwOnError)

                shouldThrow<InvalidPathException> {
                    action.applyFilesystem()
                }
            }

            "recursively move files on the same filesystem" {
                val fs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)

                val sourceNode = PathNode.of(fs.getPath("source")) {
                    file("a")
                    dir("b") {
                        file("c")
                    }
                }
                sourceNode.createFile(recursive = true)

                val targetNode = PathNode.of(fs.getPath("target"))
                targetNode.createFile()

                val expectedTargetNode = PathNode.of(fs.getPath("target")) {
                    file("a")
                    dir("b") {
                        file("c")
                    }
                }

                val action = MoveAction(sourceNode, targetNode)
                action.applyFilesystem()

                assertSoftly {
                    expectedTargetNode.exists(recursive = true).shouldBeTrue()
                    Files.notExists(sourceNode.path).shouldBeTrue()
                }
            }

            "recursively move files between filesystems" {
                val sourceFs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)
                val targetFs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)

                val sourceNode = PathNode.of(sourceFs.getPath("source")) {
                    file("a")
                    dir("b") {
                        file("c")
                    }
                }
                sourceNode.createFile(recursive = true)

                val targetNode = PathNode.of(targetFs.getPath("target"))
                targetNode.createFile()

                val expectedTargetNode = PathNode.of(targetFs.getPath("target")) {
                    file("a")
                    dir("b") {
                        file("c")
                    }
                }


                val action = MoveAction(sourceNode, targetNode, pathConverter = ::convertBasicPath)
                action.applyFilesystem()

                assertSoftly {
                    expectedTargetNode.exists(recursive = true).shouldBeTrue()
                    Files.notExists(sourceNode.path).shouldBeTrue()
                }
            }

            "move symbolic links instead of their targets" {
                val fs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)

                val symlinkTargetPath = fs.getPath("symlinkTarget")

                val sourceNode = PathNode.of(fs.getPath("source")) {
                    symlink("a", target = symlinkTargetPath)
                }
                sourceNode.createFile(recursive = true)

                val targetNode = PathNode.of(fs.getPath("target"))
                targetNode.createFile()

                val expectedTargetNode = PathNode.of(fs.getPath("target")) {
                    symlink("a", target = symlinkTargetPath)
                }

                val action = MoveAction(sourceNode, targetNode)
                action.applyFilesystem()

                expectedTargetNode.exists(recursive = true).shouldBeTrue()
            }

            "not overwrite files at the target" {
                val fs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)

                val sourceNode = PathNode.of(fs.getPath("source")) {
                    dir("a")
                    file("b")
                }
                sourceNode.createFile(recursive = true)

                val targetNode = PathNode.of(fs.getPath("target")) {
                    file("a")
                    dir("b") {
                        file("c")
                    }
                }
                targetNode.createFile(recursive = true)

                val expectedTargetNode = PathNode.of(fs.getPath("target")) {
                    file("a")
                    dir("b") {
                        file("c")
                    }
                }

                val action = MoveAction(sourceNode, targetNode)

                shouldThrow<FileAlreadyExistsException> {
                    action.applyFilesystem()
                }

                expectedTargetNode.exists(recursive = true).shouldBeTrue()
            }

            "overwrite files at the target" {
                val fs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)

                val sourceNode = PathNode.of(fs.getPath("source")) {
                    dir("a")
                    file("b")
                }
                sourceNode.createFile(recursive = true)

                val targetNode = PathNode.of(fs.getPath("target")) {
                    file("a")
                    dir("b") {
                        file("c")
                    }
                }
                targetNode.createFile(recursive = true)

                val expectedTargetNode = PathNode.of(fs.getPath("target")) {
                    dir("a")
                    file("b")
                }

                val action = MoveAction(sourceNode, targetNode, overwrite = true, onError = ::skipOnError)
                action.applyFilesystem()

                expectedTargetNode.exists(recursive = true).shouldBeTrue()
            }

            "move files atomically on the same filesystem" {
                val fs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)

                val sourceNode = PathNode.of(fs.getPath("source")) {
                    file("a")
                }
                sourceNode.createFile(recursive = true)

                val targetNode = PathNode.of(fs.getPath("target"))
                targetNode.createFile()

                val expectedTargetNode = PathNode.of(fs.getPath("target")) {
                    file("a")
                }

                val action = MoveAction(sourceNode, targetNode, atomic = true)
                action.applyFilesystem()

                expectedTargetNode.exists(recursive = true).shouldBeTrue()
            }
        }
    }
}

class CopyActionTest : WordSpec() {
    init {
        "CopyAction.applyFilesystem" should {
            "throw if source and target belong to different filesystems" {
                val sourceFs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)
                val targetFs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)

                val sourceNode = PathNode.of(sourceFs.getPath("source")) {
                    file("a")
                }
                val targetNode = PathNode.of(targetFs.getPath("target"))
                sourceNode.createFile(recursive = true)

                val action = CopyAction(sourceNode, targetNode, onError = ::throwOnError)

                shouldThrow<InvalidPathException> {
                    action.applyFilesystem()
                }
            }

            "recursively copy files on the same filesystem" {
                val fs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)

                val sourceNode = PathNode.of(fs.getPath("source")) {
                    file("a")
                    dir("b") {
                        file("c")
                    }
                }
                sourceNode.createFile(recursive = true)

                val targetNode = PathNode.of(fs.getPath("target"))
                targetNode.createFile()

                val expectedTargetNode = PathNode.of(fs.getPath("target")) {
                    file("a")
                    dir("b") {
                        file("c")
                    }
                }

                val action = CopyAction(sourceNode, targetNode)
                action.applyFilesystem()

                assertSoftly {
                    expectedTargetNode.exists(recursive = true).shouldBeTrue()
                    sourceNode.exists(recursive = true).shouldBeTrue()
                }

            }

            "recursively copy files between filesystems" {
                val sourceFs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)
                val targetFs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)

                val sourceNode = PathNode.of(sourceFs.getPath("source")) {
                    file("a")
                    dir("b") {
                        file("c")
                    }
                }
                sourceNode.createFile(recursive = true)

                val targetNode = PathNode.of(targetFs.getPath("target"))
                targetNode.createFile()

                val expectedTargetNode = PathNode.of(targetFs.getPath("target")) {
                    file("a")
                    dir("b") {
                        file("c")
                    }
                }


                val action = CopyAction(sourceNode, targetNode, pathConverter = ::convertBasicPath)
                action.applyFilesystem()

                assertSoftly {
                    expectedTargetNode.exists(recursive = true).shouldBeTrue()
                }
            }

            "copy symbolic links instead of their targets" {
                val fs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)

                // Get the absolute path to prevent a relative symlink from being created.
                val symlinkTargetPath = fs.getPath("symlinkTarget").toAbsolutePath()

                val sourceNode = PathNode.of(fs.getPath("source")) {
                    symlink("a", target = symlinkTargetPath)
                }
                sourceNode.createFile(recursive = true)

                val targetNode = PathNode.of(fs.getPath("target"))
                targetNode.createFile()

                val expectedTargetNode = PathNode.of(fs.getPath("target")) {
                    symlink("a", target = symlinkTargetPath)
                }

                val action = CopyAction(sourceNode, targetNode)
                action.applyFilesystem()

                expectedTargetNode.exists(recursive = true).shouldBeTrue()
            }

            "copy the targets of symbolic links" {
                val fs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)

                // Get the absolute path to prevent a relative symlink from being created.
                val symlinkTargetPath = fs.getPath("symlinkTarget").toAbsolutePath()
                Files.createFile(symlinkTargetPath)

                val sourceNode = PathNode.of(fs.getPath("source")) {
                    symlink("a", target = symlinkTargetPath)
                }
                sourceNode.createFile(recursive = true)

                val targetNode = PathNode.of(fs.getPath("target"))
                targetNode.createFile()

                val expectedTargetNode = PathNode.of(fs.getPath("target")) {
                    file("a")
                }

                val action = CopyAction(sourceNode, targetNode, followLinks = true)
                action.applyFilesystem()

                expectedTargetNode.exists(recursive = true).shouldBeTrue()
            }

            "not overwrite files at the target" {
                val fs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)

                val sourceNode = PathNode.of(fs.getPath("source")) {
                    dir("a")
                    file("b")
                }
                sourceNode.createFile(recursive = true)

                val targetNode = PathNode.of(fs.getPath("target")) {
                    file("a")
                    dir("b") {
                        file("c")
                    }
                }
                targetNode.createFile(recursive = true)

                val expectedTargetNode = PathNode.of(fs.getPath("target")) {
                    file("a")
                    dir("b") {
                        file("c")
                    }
                }

                val action = CopyAction(sourceNode, targetNode)

                shouldThrow<FileAlreadyExistsException> {
                    action.applyFilesystem()
                }

                expectedTargetNode.exists(recursive = true).shouldBeTrue()
            }

            "overwrite files at the target" {
                val fs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)

                val sourceNode = PathNode.of(fs.getPath("source")) {
                    dir("a")
                    file("b")
                }
                sourceNode.createFile(recursive = true)

                val targetNode = PathNode.of(fs.getPath("target")) {
                    file("a")
                    dir("b") {
                        file("c")
                    }
                }
                targetNode.createFile(recursive = true)

                val expectedTargetNode = PathNode.of(fs.getPath("target")) {
                    dir("a")
                    file("b")
                }

                val action = CopyAction(sourceNode, targetNode, overwrite = true, onError = ::skipOnError)
                action.applyFilesystem()

                expectedTargetNode.exists(recursive = true).shouldBeTrue()
            }
        }
    }
}

class DeleteActionTest : WordSpec() {
    init {
        "DeleteAction.applyFilesystem" should {
            "recursively delete files" {
                val fs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)
                val targetNode = PathNode.of(fs.getPath("target")) {
                    file("a")
                    dir("b") {
                        dir("c") {
                            file("d")
                        }
                        dir("e")
                    }
                }
                targetNode.createFile(recursive = true)

                val action = DeleteAction(targetNode)
                action.applyFilesystem()

                Files.notExists(fs.getPath("target")).shouldBeTrue()
            }

            "delete links instead of their targets" {
                val fs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)

                val linkTarget = fs.getPath("linkTarget")
                Files.createFile(linkTarget)

                val targetNode = PathNode.of(fs.getPath("target")) {
                    symlink("link", target = linkTarget)
                }
                targetNode.createFile(recursive = true)

                val action = DeleteAction(targetNode)
                action.applyFilesystem()

                Files.exists(linkTarget).shouldBeTrue()
            }
        }
    }
}
