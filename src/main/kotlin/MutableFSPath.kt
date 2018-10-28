package diffir

import java.io.IOException
import java.nio.file.*
import java.util.*
import kotlin.streams.toList

/**
 * Create a [MutableFilePath] or [MutableDirPath] from a [Path].
 */
private fun createPath(path: Path): MutableFSPath {
    return when {
        Files.isDirectory(path) -> MutableDirPath(path)
        else -> MutableFilePath(path)
    }
}

/**
 * A mutable representation of a file or directory path.
 */
abstract class MutableFSPath(
    final override val fileName: Path,
    final override val parent: MutableDirPath?
) : FSPath {
    init {
        require(fileName.parent == null) { "The given file name must not have a parent." }

        require(parent == null || fileName.fileSystem == parent.path.fileSystem) {
            "The given file name must be associated with the same filesystem as the given parent."
        }
    }

    override val path: Path
        get() = parent?.path?.resolve(fileName) ?: fileName

    /**
     * The ancestor whose [parent] is `null`.
     */
    internal val root: MutableFSPath
        get() {
            var current = this
            while (true) {
                current = current.parent ?: break
            }
            return current
        }

    /**
     * Constructs a new path from the given [path].
     *
     * This recursively sets the [parent] property so that a hierarchy of [MutableFSPath] objects going all the way up
     * to the root is returned. The root component of the path will be its own [MutableFSPath].
     */
    constructor(path: Path) : this(
        path.fileName ?: path,
        if (path.parent == null) null else MutableDirPath(path.parent)
    )

    /**
     * Constructs a new path from the given segments without path separators.
     *
     * The constructed path will be associated with the default filesystem.
     *
     * This recursively sets the [parent] property so that a hierarchy of [MutableFSPath] objects going all the way up
     * to the root is returned. The root component of the path will be its own [MutableFSPath].
     *
     * @param [firstSegment] The first segment of the path.
     * @param [segments] The remaining segments of the path.
     *
     * @see [Paths.get]
     */
    constructor(firstSegment: String, vararg segments: String) : this(Paths.get(firstSegment, *segments))

    override fun toString(): String = path.toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as MutableFSPath
        return fileName == other.fileName && parent == other.parent
    }

    override fun hashCode(): Int = Objects.hash(fileName, parent)

    abstract override fun copy(fileName: Path, parent: DirPath?): MutableFSPath
}

/**
 * A mutable representation of a file path.
 */
class MutableFilePath : MutableFSPath, FilePath {
    init {
        parent?._children?.add(this)
    }

    constructor(path: Path, parent: MutableDirPath?) : super(path, parent)

    constructor(path: Path) : super(path)

    constructor(firstSegment: String, vararg segments: String) : super(firstSegment, *segments)

    /**
     * Returns whether the file represented by this path exists in the filesystem.
     *
     * @param [checkType] Check not only whether the file exists, but also whether it is a normal file.
     */
    override fun exists(checkType: Boolean): Boolean =
        if (checkType) Files.isRegularFile(path) else Files.exists(path)

    override fun copy(fileName: Path, parent: DirPath?): MutableFilePath =
        MutableFilePath(fileName, parent as MutableDirPath?)
}

/**
 * A mutable representation of a directory path.
 */
class MutableDirPath : MutableFSPath, DirPath {
    init {
        parent?._children?.add(this)
    }

    /**
     * The backing property of [children]. This is used to add new children to the set without changing their parent.
     */
    internal val _children: MutableSet<MutableFSPath> = mutableSetOf()

    /**
     * A mutable representation of the paths of the immediate children of the directory.
     *
     * Whenever children are added to this set, a copy of each of them is made with the [parent] property set to this
     * directory.
     */
    override val children: MutableSet<MutableFSPath> = PathChildren(this, _children)

    /**
     * A mutable representation of the paths of all descendants of the directory.
     *
     * When items are added to this set, a copy is inserted into the proper location in the tree. Both relative and
     * absolute paths can be added to the set. Relative paths are considered to be relative to this directory, and
     * absolute paths must start with this directory. Items which are removed from the set are removed from their
     * location in the tree.
     */
    override val descendants: MutableSet<MutableFSPath> = PathDescendants(this)

    constructor(path: Path, parent: MutableDirPath?) : super(path, parent)

    constructor(path: Path) : super(path)

    constructor(firstSegment: String, vararg segments: String) : super(firstSegment, *segments)

    /**
     * Returns whether the file represented by this path exists in the filesystem.
     *
     * @param [checkType] Check not only whether the file exists, but also whether it is a directory.
     */
    override fun exists(checkType: Boolean): Boolean =
        if (checkType) Files.isDirectory(path) else Files.exists(path)

    /**
     * Returns a copy of this path.
     *
     * Children of this path are copied deeply.
     *
     * @param [fileName] The new file name to assign to the copy.
     * @param [parent] The new parent to assign to the copy.
     */
    override fun copy(fileName: Path, parent: DirPath?): MutableDirPath {
        val new = MutableDirPath(fileName, parent as MutableDirPath?)
        new.children.addAll(children.map { it.copy() })
        return new
    }

    override fun <T : FSPath> relativize(other: T): T {
        require(other.startsWith(this)) { "The given path must start with this path." }
        require(path.isAbsolute == other.path.isAbsolute) {
            "Either both paths must be absolute or both paths must be relative."
        }

        return other.replaceAncestor(this, null)
    }

    override fun <T : FSPath> resolve(other: T): T = other.replaceAncestor(null, this)

    override fun walkChildren(): Sequence<MutableFSPath> =
        super.walkChildren().map { it as MutableFSPath }

    /**
     * Populates [children] with paths from the filesystem.
     *
     * This method reads the filesystem to get the list of immediate children of the directory represented by this
     * object. It then creates path objects from those file paths and populates [children] with them.
     *
     * @throws [IOException] This exception is thrown if the path represented by this object is not the path of an
     * accessible directory or if there is an IO error.
     */
    fun findChildren() {
        val childPaths = Files.list(path).map { createPath(it) }.toList()
        children.addAll(childPaths)
    }

    /**
     * Populates [descendants] with paths from the filesystem.
     *
     * This method reads the filesystem to get the list of descendants of the directory represented by this object. It
     * then creates path objects from those file paths and populates [descendants] with them.
     *
     * @param [followLinks] Follow symbolic links when walking the directory tree.
     *
     * @throws [IOException] This exception is thrown if the path represented by this object is not the path of an
     * accessible directory or if there is an IO error.
     */
    fun findDescendants(followLinks: Boolean = false) {
        val visitOptions = mutableSetOf<FileVisitOption>()
        if (followLinks) visitOptions.add(FileVisitOption.FOLLOW_LINKS)

        val descendantPaths = Files.walk(path, *visitOptions.toTypedArray()).map { createPath(it) }.toList()
        descendants.addAll(descendantPaths)
    }

    /**
     * A type-safe builder for creating a new file path from the given path segments.
     *
     * This is meant to be used with [of] and [dir] to construct trees of file and directory paths.
     *
     * @param [firstSegment] The first segment of the new path.
     * @param [segments] The remaining segments of the new path.
     *
     * @see [of]
     */
    fun file(firstSegment: String, vararg segments: String): MutableFilePath {
        val newPath = path.fileSystem.getPath(firstSegment, *segments)
        val filePath = MutableFilePath(newPath)
        children.add(filePath.root)
        return filePath
    }

    /**
     * A type-safe builder for creating a new directory path from the given path segments and its children.
     *
     * This is meant to be used with [of] and [file] to construct trees of file and directory paths.
     *
     * @param [firstSegment] The first segment of the new path.
     * @param [segments] The remaining segments of the new path.
     * @param [init] A function literal with receiver in which you can call [file] and [dir] to construct children.
     *
     * @see [of]
     */
    fun dir(
        firstSegment: String, vararg segments: String,
        init: MutableDirPath.() -> Unit = {}
    ): MutableDirPath {
        val newPath = path.fileSystem.getPath(firstSegment, *segments)
        val dirPath = MutableDirPath(newPath)
        dirPath.init()
        children.add(dirPath.root)
        return dirPath
    }

    companion object {
        /**
         * Constructs a new directory path from the given path and its children.
         *
         * This method is a type-safe builder. It allows you to create a tree of file and directory paths. The [init]
         * parameter accepts a lambda in which you can call [file] and [dir] to create new file and directory paths as
         * children of this path.
         *
         * The whole tree of file and directory paths will be associated with the same filesystem as [path].
         *
         * @param [path] The path to construct the new directory path from.
         * @param [init] A function with receiver in which you can call [file] and [dir] to construct children.
         *
         * Example:
         * ```
         * val path = Paths.get("/", "home", "user")
         *
         * val dirPath = MutableDirPath.of(path) {
         *     file("Photo.png")
         *     dir("Documents", "Reports") {
         *         file("Quarterly.odt")
         *         file("Monthly.odt")
         *     }
         * }
         * ```
         *
         * @return A new directory path containing the given children.
         */
        fun of(path: Path, init: MutableDirPath.() -> Unit): MutableDirPath {
            val dirPath = MutableDirPath(path)
            dirPath.init()
            return dirPath
        }
    }
}
