package diffir

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * A type of file in the filesystem.
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
     * Creates a file of this type at the given [path] in the filesystem.
     *
     * @throws [IOException] An I/O error occurred while creating the file.
     */
    fun createFile(path: Path)

    companion object {
        /**
         * Returns a [FileType] instance based on the file at the given [path].
         */
        fun fromFilesystem(path: Path): FileType = when {
            Files.isRegularFile(path) -> RegularFileType()
            Files.isDirectory(path) -> DirectoryType()
            Files.isSymbolicLink(path) -> SymbolicLinkType(Files.readSymbolicLink(path))
            else -> UnknownType()
        }
    }
}

/**
 * A type representing a regular file.
 */
class RegularFileType : FileType {
    override fun checkType(path: Path): Boolean = Files.isRegularFile(path)

    override fun createFile(path: Path) {
        Files.createFile(path)
    }
}

/**
 * A type representing a directory.
 */
class DirectoryType : FileType {
    override fun checkType(path: Path): Boolean = Files.isDirectory(path)

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

    override fun createFile(path: Path) {
        Files.createSymbolicLink(path, target)
    }
}

/**
 * A type representing a file whose type is unknown.
 */
class UnknownType : FileType {
    override fun checkType(path: Path): Boolean = false

    override fun createFile(path: Path) {}
}
