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

    override val root: MutableFSPath
        get() = walkAncestors().lastOrNull() ?: this

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

    override fun walkAncestors(direction: WalkDirection): Sequence<MutableDirPath> = sequence {
        parent?.let {
            if (direction == WalkDirection.BOTTOM_UP) yield(it)
            yieldAll(it.walkAncestors(direction))
            if (direction == WalkDirection.TOP_DOWN) yield(it)
        }
    }
}

/**
 * A mutable representation of a file path.
 */
class MutableFilePath : MutableFSPath, FilePath {
    init {
        parent?.addChild(this)
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
        parent?.addChild(this)
    }

    /**
     * The backing property of [children].
     *
     * This is a map that maps each child to itself so that children can be quickly retrieved using another path that is
     * equal. This is also used to add new children to the set without changing their parent.
     */
    private val _children: MutableMap<MutableFSPath, MutableFSPath> = mutableMapOf()

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
     * Adds a child to [children] without setting its parent.
     *
     * @return `true` if the child has been added, `false` if the child is already contained in [children].
     */
    internal fun addChild(child: MutableFSPath): Boolean = _children.put(child, child) != null

    /**
     * Returns the child that is equal to [searchKey] or `null` if there is none.
     */
    // This unchecked cast is okay because two [MutableFSPath] objects are only equal if they are the same type.
    @Suppress("UNCHECKED_CAST")
    internal fun <T : MutableFSPath> getChild(searchKey: T): T? = _children[searchKey] as T?

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

    @Suppress("UNCHECKED_CAST")
    override fun <T : FSPath> relativize(other: T): T {
        require(other.startsWith(this)) { "The given path must start with this path." }
        require(path.isAbsolute == other.path.isAbsolute) {
            "Either both paths must be absolute or both paths must be relative."
        }

        // Implementations of [FSPath.copy] should always return the type of their class. Because Kotlin does not have
        // self types to enforce this, an unsafe cast must be used.

        // Climb the tree of ancestors of [other] until we find the ancestor equal to this path. Then replace that
        // ancestor with `null`. Finally, climb back down the tree and return the copy of the original path.

        var childOfAncestor = other.walkAncestors().find { it.parent == this } ?: other

        childOfAncestor = childOfAncestor.copy(parent = null)

        if (childOfAncestor !is DirPath) return childOfAncestor as T

        return (childOfAncestor.walkChildren().find { other.endsWith(it) } ?: childOfAncestor) as T
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : FSPath> resolve(other: T): T {
        if (other.path.isAbsolute) return other

        // Implementations of [FSPath.copy] should always return the type of their class. Because Kotlin does not have
        // self types to enforce this, an unsafe cast must be used.

        // Get the root node of [other]. Then set the parent of that node to be this path. Finally, climb back down the
        // tree and return the copy of the original path.

        var childOfAncestor = other.root

        childOfAncestor = childOfAncestor.copy(parent = this)

        if (childOfAncestor !is DirPath) return childOfAncestor as T

        return (childOfAncestor.walkChildren().find { it.endsWith(other) } ?: childOfAncestor) as T
    }

    override fun walkChildren(direction: WalkDirection): Sequence<MutableFSPath> = sequence {
        for (child in children) {
            if (direction == WalkDirection.TOP_DOWN) yield(child)
            if (child is MutableDirPath) yieldAll(child.walkChildren(direction))
            if (direction == WalkDirection.BOTTOM_UP) yield(child)
        }
    }

    override fun <T : MutableFSPath> findDescendant(searchKey: T): T? {
        require(searchKey.startsWith(this)) { "The given path must start with this path." }

        // Drop the first ancestor because it will be equal to this path.
        val ancestors = searchKey.walkAncestors(WalkDirection.TOP_DOWN).drop(1)
        var parentOfDescendant = this

        for (ancestor in ancestors) {
            parentOfDescendant = parentOfDescendant.getChild(ancestor) ?: return null
        }

        return parentOfDescendant.getChild(searchKey)
    }

    /**
     * Populates [children] with paths from the filesystem.
     *
     * This method reads the filesystem to get the list of immediate children of the directory represented by this
     * object. It then creates path objects from those file paths and populates [children] with them.
     *
     * @throws [IOException] This exception is thrown if the path represented by this object is not the path of an
     * accessible directory or if there is an IO error.
     */
    fun scanChildren() {
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
    fun scanDescendants(followLinks: Boolean = false) {
        val visitOptions = mutableSetOf<FileVisitOption>()
        if (followLinks) visitOptions.add(FileVisitOption.FOLLOW_LINKS)

        val descendantPaths = Files.walk(path, *visitOptions.toTypedArray())
            .skip(1) // Skip the directory itself.
            .map { createPath(it) }
            .toList()

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
