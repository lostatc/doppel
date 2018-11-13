package diffir.path

import diffir.filesystem.getFileChecksum
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * A type of file in the filesystem.
 *
 * This class provides a common interface for interacting with the filesystem for different types of files.
 */
interface FileType {
    /**
     * Returns whether the file at the given [path] is the type of file represented by this class.
     *
     * @return `true` if the type of the file matches or `false` if it does not match, the file does not exist or the
     * type cannot be determined.
     */
    fun checkType(path: Path): Boolean

    /**
     * Returns whether the files [left] and [right] have the same contents.
     *
     * @throws [IOException] An I/O error occurred.
     */
    fun checkSame(left: Path, right: Path): Boolean

    /**
     * Creates a file of this type at the given [path] in the filesystem.
     *
     * @throws [IOException] An I/O error occurred while creating the file.
     */
    fun createFile(path: Path)

    /**
     * Returns the file type that is most appropriate for a given [PathNode].
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
    override fun checkType(path: Path): Boolean = Files.isRegularFile(path)

    /**
     * Returns whether the files [left] and [right] have the same size and checksum.
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
}

/**
 * A type representing a directory.
 */
class DirectoryType : FileType {
    override fun checkType(path: Path): Boolean = Files.isDirectory(path)

    /**
     * Returns whether the directories [left] and [right] contain the same paths.
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
}

/**
 * A type representing a symbolic link.
 *
 * @property [target] The path the link points to.
 */
class SymbolicLinkType(val target: Path) : FileType {
    override fun checkType(path: Path): Boolean = Files.isSymbolicLink(path)

    /**
     * Returns whether the symbolic links [left] and [right] point to the same path.
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
}

/**
 * Returns the [FileType] that is most appropriate for file at the given [path].
 */
fun fileTypeFromFilesystem(path: Path): FileType = when {
    Files.isRegularFile(path) -> RegularFileType()
    Files.isDirectory(path) -> DirectoryType()
    Files.isSymbolicLink(path) -> SymbolicLinkType(Files.readSymbolicLink(path))
    else -> UnknownType()
}
