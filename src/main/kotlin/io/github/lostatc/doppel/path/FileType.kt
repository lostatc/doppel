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

import io.github.lostatc.doppel.filesystem.getFileChecksum
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * A type of file in the file system.
 *
 * This interface is for interacting with the file system in a way that is dependent on the type of file being
 * represented. Implementations of this interface are immutable.
 */
interface FileType {
    /**
     * Returns whether the file at the given [path] is the type of file represented by this class.
     *
     * This does not follow links.
     *
     * @return `true` if the type of the file matches or `false` if it does not match, the file does not exist or the
     * type cannot be determined.
     */
    fun checkType(path: Path): Boolean

    /**
     * Returns whether the files [left] and [right] have the same contents.
     *
     * Both [left] and [right] must be files of this type. This does not follow links.
     *
     * @throws [IOException] An I/O error occurred.
     */
    fun checkSame(left: Path, right: Path): Boolean

    /**
     * Creates a file of this type at the given [path] in the file system.
     *
     * @throws [IOException] An I/O error occurred while creating the file.
     */
    fun createFile(path: Path)

    /**
     * Returns the file type that is most appropriate for a given [PathNode].
     *
     * This method should return this object if no other [FileType] implementation is more appropriate.
     */
    fun getFileType(pathNode: PathNode): FileType = when {
        pathNode.children.isNotEmpty() -> DirectoryType()
        else -> this
    }
}

/**
 * A type representing a regular file.
 */
class RegularFileType : FileType {
    override fun checkType(path: Path): Boolean = Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)

    /**
     * Returns whether the files [left] and [right] have the same size and checksum.
     *
     * Both [left] and [right] must be regular files. This does not follow links.
     *
     * @throws [IOException] An I/O error occurred.
     */
    override fun checkSame(left: Path, right: Path): Boolean {
        if (Files.isSameFile(left, right)) return true
        if (Files.size(left) != Files.size(right)) return false
        return getFileChecksum(left) contentEquals getFileChecksum(right)
    }

    override fun createFile(path: Path) {
        Files.createFile(path)
    }

    override fun equals(other: Any?): Boolean = other is RegularFileType

    override fun hashCode(): Int = this::class.hashCode()
}

/**
 * A type representing a directory.
 */
class DirectoryType : FileType {
    override fun checkType(path: Path): Boolean = Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)

    /**
     * Returns whether the directories [left] and [right] contain the same paths.
     *
     * Both [left] and [right] must be directories. This does not follow links.
     *
     * @throws [IOException] An I/O error occurred.
     */
    override fun checkSame(left: Path, right: Path): Boolean {
        if (Files.isSameFile(left, right)) return true
        return Files.list(left) == Files.list(right)
    }

    override fun createFile(path: Path) {
        Files.createDirectory(path)
    }

    override fun equals(other: Any?): Boolean = other is DirectoryType

    override fun hashCode(): Int = this::class.hashCode()
}

/**
 * A type representing a symbolic link.
 *
 * @property [target] The path the link points to.
 */
data class SymbolicLinkType(val target: Path) : FileType {
    override fun checkType(path: Path): Boolean = Files.isSymbolicLink(path)

    /**
     * Returns whether the symbolic links [left] and [right] point to the same path.
     *
     * Both [left] and [right] must be symbolic links. This does not follow links.
     *
     * @throws [IOException] An I/O error occurred.
     */
    override fun checkSame(left: Path, right: Path): Boolean {
        if (Files.isSameFile(left, right)) return true
        return Files.readSymbolicLink(left) == Files.readSymbolicLink(right)
    }

    override fun createFile(path: Path) {
        Files.createSymbolicLink(path, target)
    }
}

/**
 * A type representing a file whose type is unknown.
 */
class UnknownType : FileType {
    override fun checkType(path: Path): Boolean = false

    override fun checkSame(left: Path, right: Path): Boolean = false

    override fun createFile(path: Path) {}

    override fun equals(other: Any?): Boolean = other is UnknownType

    override fun hashCode(): Int = this::class.hashCode()
}

/**
 * Returns the [FileType] that is most appropriate for file at the given [path].
 *
 * @throws [IOException] An I/O error occurred.
 */
fun fileTypeFromFileSystem(path: Path): FileType = when {
    Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) -> RegularFileType()
    Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) -> DirectoryType()
    Files.isSymbolicLink(path) -> SymbolicLinkType(Files.readSymbolicLink(path))
    else -> UnknownType()
}
