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

import io.kotlintest.matchers.maps.shouldContainValue
import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

class PathNodeBuildersKtTest : WordSpec() {
    init {
        "MutablePathNode.pathNode" should {
            "return a path node built from segments" {
                val testNode = MutablePathNode.of("/")
                val expectedNode = MutablePathNode.of("/", "a", "b", type = RegularFileType())

                testNode.pathNode("a", "b", type = RegularFileType()).shouldBe(expectedNode)
            }

            "add a descendant to this node" {
                val testNode = MutablePathNode.of("/")
                val expectedNode = MutablePathNode.of("/", "a", "b", type = RegularFileType())

                testNode.pathNode("a", "b", type = RegularFileType())

                testNode.descendants.shouldContainValue(expectedNode)
            }
        }
    }
}