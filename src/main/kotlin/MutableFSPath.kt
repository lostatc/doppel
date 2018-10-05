package diffir

import java.nio.file.Path
import java.io.IOException
import java.util.Objects

/**
 * Return a list of paths of the immediate children of [directory] in the filesystem.
 */
internal fun scanChildren(directory: DirPath): List<MutableFSPath> {
    val dirChildren = directory.toFile().listFiles()
    dirChildren ?: throw IOException(
        "cannot access children because the path is not an accessible directory or because of an IO error")
    return dirChildren.map {
        when {
            it.isDirectory -> MutableDirPath(it.toPath().fileName)
            else -> MutableFilePath(it.toPath().fileName)
        }
    }
}

/**
 * Return a list of paths of the descendants of [directory] in the filesystem.
 */
private fun scanDescendants(directory: DirPath): List<MutableFSPath> =
    scanChildren(directory)
    .asSequence()
    .map { it.withAncestor(directory) }
    .map { if (it is MutableDirPath) scanDescendants(it) + it else listOf(it) }
    .flatten()
    .toList()

/**
 * Return a hierarchy of paths from a list of path segments.
 */
private fun getTreeFromSegments(segments: List<String>): MutableDirPath? =
    if (segments.isEmpty()) null else MutableDirPath(segments)

/**
 * A mutable representation of a file or directory path.
 *
 * This class contains properties and methods common to all mutable paths.
 */
abstract class MutableFSPath(override val fileName: String, override val parent: MutableDirPath?) : FSPath {
    /**
     * Constructs a new directory path from the given path [segments] without path separators.
     *
     * @return A hierarchy of [MutableFSPath] objects where the last segment becomes the new path's [fileName], and
     * the rest of them become the new path's parent and ancestors.
     */
    constructor(segments: List<String>) : this(segments.last(), getTreeFromSegments(segments.dropLast(1)))

    /**
     * Constructs a new directory path from the given path segments without path separators.
     *
     * @param [firstSegment] The first segment of the new path.
     * @param [segments] The remaining segments of the new path.
     *
     * @return A hierarchy of [MutableFSPath] objects where the last segment becomes the new path's [fileName], and
     * the rest of them become the new path's parent and ancestors.
     */
    constructor(firstSegment: String, vararg segments: String) : this(listOf(firstSegment, *segments))

    /**
     * Constructs a new directory path from the segments of the given [path].
     *
     * @return A hierarchy of [MutableFSPath] objects where the last segment becomes the new path's [fileName], and
     * the rest of them become the new path's parent and ancestors.
     */
    constructor(path: Path) : this(path.map { it.toString() })

    /**
     * Returns the ancestor whose [parent] is `null`.
     */
    internal fun getRoot(): MutableFSPath {
        var current = this
        while (true) {
            current = current.parent ?: break
        }
        return current
    }
    override fun toString(): String = toPath().toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as MutableFSPath
        return fileName == other.fileName && parent == other.parent
    }

    override fun hashCode(): Int = Objects.hash(fileName, parent)

    abstract override fun copy(fileName: String, parent: DirPath?): MutableFSPath

    override fun relativeTo(ancestor: DirPath): MutableFSPath {
        if (!startsWith(ancestor))
        throw IllegalArgumentException("the given path must be an ancestor of this path")

        var current = this
        while (current.parent != ancestor) {
            current = current.parent ?: break
        }
        return current.copy(parent = null)
    }

    override fun withAncestor(ancestor: DirPath): MutableFSPath =
        getRoot().copy(parent = ancestor)
}

/**
 * A mutable representation of a file path.
 */
class MutableFilePath : MutableFSPath, FilePath {
    init {
        parent?._children?.add(this)
    }

    constructor(fileName: String, parent: MutableDirPath?) : super(fileName, parent)

    constructor(segments: List<String>) : super(segments)

    constructor(firstSegment: String, vararg segments: String) : super(firstSegment, *segments)

    constructor(path: Path) : super(path)

    /**
     * Returns whether the file represented by this path exists in the filesystem.
     *
     * @param [checkType] Check not only whether the file exists, but also whether it is a normal file.
     */
    override fun exists(checkType: Boolean): Boolean = if (checkType) toFile().isFile else toFile().exists()

    override fun copy(fileName: String, parent: DirPath?): MutableFilePath {
        return MutableFilePath(fileName, parent as MutableDirPath?)
    }

    override fun relativeTo(ancestor: DirPath): MutableFilePath = super.relativeTo(ancestor) as MutableFilePath

    override fun withAncestor(ancestor: DirPath): MutableFilePath = super.withAncestor(ancestor) as MutableFilePath
}

/**
 * A mutable representation of a directory path.
 *
 * To create new instances of this class, see the factory method [of].
 */
class MutableDirPath : MutableFSPath, DirPath {
    init {
        parent?._children?.add(this)
    }

    constructor(fileName: String, parent: MutableDirPath?) : super(fileName, parent)

    constructor(segments: List<String>) : super(segments)

    constructor(firstSegment: String, vararg segments: String) : super(firstSegment, *segments)

    constructor(path: Path) : super(path)

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
     * Items added to the set are inserted into their proper location in the tree. Both relative and absolute paths can
     * be added to the set. Relative paths are considered to be relative to this directory, and absolute paths must
     * start with this directory. Items which are removed from the set are removed from their location in the tree.
     */
    override val descendants: MutableSet<MutableFSPath> = PathDescendants(this)

    /**
     * Returns whether the file represented by this path exists in the filesystem.
     *
     * @param [checkType] Check not only whether the file exists, but also whether it is a directory.
     */
    override fun exists(checkType: Boolean): Boolean = if (checkType) toFile().isDirectory else toFile().exists()

    /**
     * Returns a copy of this path.
     *
     * Children of this path are copied deeply.
     */
    override fun copy(fileName: String, parent: DirPath?): MutableDirPath {
        val new = MutableDirPath(fileName, parent as MutableDirPath?)
        new.children.addAll(children.map { it.copy() })
        return new
    }

    override fun relativeTo(ancestor: DirPath): MutableDirPath = super.relativeTo(ancestor) as MutableDirPath

    override fun withAncestor(ancestor: DirPath): MutableDirPath = super.withAncestor(ancestor) as MutableDirPath

    override fun walkChildren(): Sequence<MutableFSPath> = super.walkChildren().map { it as MutableFSPath }

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
        children.addAll(scanChildren(this))
    }

    /**
     * Populates [descendants] with paths from the filesystem.
     *
     * This method reads the filesystem to get the list of descendants of the directory represented by this object. It
     * then creates path objects from those file paths and populates [descendants] with them.
     *
     * @throws [IOException] This exception is thrown if the path represented by this object is not the path of an
     * accessible directory or if there is an IO error.
     */
    fun findDescendants() {
        descendants.addAll(scanDescendants(this))
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
        val filePath = MutableFilePath(firstSegment, *segments)
        children.add(filePath.getRoot())
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
    fun dir(firstSegment: String, vararg segments: String, init: MutableDirPath.() -> Unit = {}): MutableDirPath {
        val dirPath = MutableDirPath(firstSegment, *segments)
        dirPath.init()
        children.add(dirPath.getRoot())
        return dirPath
    }

    companion object {
        /**
         * Constructs a new directory path from the given path segments and its children.
         *
         * This method is a type-safe builder. It allows you to create a tree of file and directory paths. The [init]
         * parameter accepts a lambda in which you can call [file] and [dir] to create new file and directory paths as
         * children of this path.
         *
         * @param [firstSegment] The first segment of the new path.
         * @param [segments] The remaining segments of the new path.
         * @param [init] A function literal with receiver in which you can call [file] and [dir] to construct children.
         *
         * Example:
         * ```
         * MutableDirPath.of("/", "home", "user") {
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
        fun of(firstSegment: String, vararg segments: String, init: MutableDirPath.() -> Unit): MutableDirPath {
            val dirPath = MutableDirPath(firstSegment, *segments)
            dirPath.init()
            return dirPath
        }
    }
}
