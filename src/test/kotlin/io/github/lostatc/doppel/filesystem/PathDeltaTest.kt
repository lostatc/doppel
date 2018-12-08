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
import io.github.lostatc.doppel.handlers.SkipHandler
import io.github.lostatc.doppel.path.PathNode
import io.github.lostatc.doppel.path.RegularFileType
import io.github.lostatc.doppel.path.dir
import io.github.lostatc.doppel.path.file
import io.github.lostatc.doppel.testing.DEFAULT_JIMFS_CONFIG
import io.kotlintest.assertSoftly
import io.kotlintest.matchers.boolean.shouldBeTrue
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.WordSpec

class PathDeltaTest : WordSpec() {
    init {
        "PathDelta.undo" should {
            "throw if the given number of changes is negative" {
                val delta = PathDelta()

                shouldThrow<IllegalArgumentException> {
                    delta.undo(-1)
                }
            }

            "return the number of changes undone" {
                val delta = PathDelta()

                delta.add(CreateAction(PathNode.of("")))

                assertSoftly {
                    delta.undo(2).shouldBe(1)
                    delta.undo(1).shouldBe(0)
                }
            }
        }

        "PathDelta.clear" should {
            "return the number of changes cleared" {
                val delta = PathDelta()

                delta.add(CreateAction(PathNode.of("")))

                delta.clear().shouldBe(1)
            }
        }

        "PathDelta.view" should {
            "return a view of the file system" {
                val delta = PathDelta()
                val fs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)

                delta.add(
                    MoveAction(PathNode.of(fs.getPath("/", "source")), PathNode.of(fs.getPath("/", "target"))),
                    CreateAction(PathNode.of(fs.getPath("/", "target", "new"), type = RegularFileType()))
                )

                val expectedNode = PathNode.of(fs.getPath("/")) {
                    dir("target") {
                        file("new")
                    }
                }

                delta.view(PathNode.of(fs.getPath("/"))).shouldBe(expectedNode)
            }
        }

        "PathDelta.apply" should {
            "apply the changes" {
                val delta = PathDelta()
                val fs = Jimfs.newFileSystem(DEFAULT_JIMFS_CONFIG)

                val testNode = PathNode.of(fs.getPath("/")) {
                    dir("source")
                }
                testNode.createFile(recursive = true, errorHandler = SkipHandler())

                val expectedNode = PathNode.of(fs.getPath("/")) {
                    dir("target") {
                        file("new")
                    }
                }

                delta.add(
                    MoveAction(PathNode.of(fs.getPath("/", "source")), PathNode.of(fs.getPath("/", "target"))),
                    CreateAction(PathNode.of(fs.getPath("/", "target", "new"), type = RegularFileType()))
                )
                delta.apply()

                expectedNode.exists(recursive = true).shouldBeTrue()
            }
        }
    }
}