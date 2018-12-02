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

package io.github.lostatc.doppel.handlers

import java.nio.file.FileSystem
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * A function which converts a [Path] object to a [Path] of the given [FileSystem].
 *
 * This is only required to handle relative paths.
 *
 * @return A [Path] that is equivalent to the given [Path] and associated with the given [FileSystem].
 *
 * @throws [InvalidPathException] The given [Path] cannot be converted to a [Path] of the given [FileSystem].
 */
typealias PathConverter = (Path, FileSystem) -> Path

/**
 * A [PathConverter] that doesn't convert paths between file systems.
 *
 * @throws [InvalidPathException] [path] is not associated with [fileSystem].
 */
fun neverConvert(path: Path, fileSystem: FileSystem): Path {
    if (path.fileSystem == fileSystem) {
        return path
    } else {
        throw InvalidPathException(path.toString(), "The given path can not be converted to the given file system.")
    }
}
