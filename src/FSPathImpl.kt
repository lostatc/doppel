package diffir

import java.nio.file.Path
import java.nio.file.Paths
import java.io.File

/**
 * This exception is thrown when there is a problem caused by a path being absolute.
 */
class IsAbsolutePathException(message: String) : Exception(message)

/**
 * This exception is thrown when there is a problem caused by a path not being an accessible directory.
 */
class NotADirectoryException(message: String) : Exception(message)

/**
 * A mutable representation of a file or directory path.
 *
 * This class contains properties and methods common to all mutable paths.
 *
 * @constructor Accepts the [relativeSegments] of a file or directory path without path separators and creates a new
 * path.
 */
abstract class MutableFSPath(override vararg val relativeSegments: String) : FSPath {
    override var parent: MutableFSPath? = null
        set(value) {
            if (containsRoot && value != null) {
                throw IsAbsolutePathException("absolute paths cannot have a parent")
            } else {
                field = value
            }
        }

    /**
     * This path, excluding parents, is absolute.
     */
    private val containsRoot: Boolean
        get() = Paths.get("", *relativeSegments).isAbsolute

    /**
     * Creates a new path from the given [path].
     */
    constructor(path: Path) : this(*path.map { it.toString() }.toTypedArray())

    override fun toString(): String = toPath().toString()

    override fun equals(other: Any?) =
            if (other is FSPath && this::class == other::class) pathSegments == other.pathSegments else false

    override fun hashCode(): Int = pathSegments.hashCode()

    override fun relativeTo(ancestor: DirPathBase): MutableFSPath {
        val new = copy()
        var current = new
        while (current.parent != ancestor && current.parent != null) {
            current.parent = current.parent?.copy()
            current = current.parent!!
        }
        current.parent = null
        return new
    }
}

/**
 * A mutable representation of a file path.
 */
class FilePath : MutableFSPath, FilePathBase {

    constructor(vararg relativeSegments: String) : super(*relativeSegments)

    constructor(path: Path) : super(path)

    /**
     * Returns whether the file represented by this path exists in the filesystem.
     *
     * @param checkType: Check not only whether the file exists, but also whether it is a normal file.
     */
    override fun exists(checkType: Boolean): Boolean = if (checkType) toFile().isFile else toFile().exists()

    override fun copy(): FilePath {
        val new = FilePath(*relativeSegments)
        new.parent = parent
        return new
    }

    /**
     * Create a read-only view of this file path.
     */
    fun toView(): FilePathView = FilePathView(this)
}

/**
 * A mutable representation of a directory path.
 */
class DirPath : MutableFSPath, DirPathBase {
    override var children: MutableSet<MutableFSPath> = mutableSetOf()

    constructor(vararg segments: String) : super(*segments)

    constructor(path: Path) : super(path)

    /**
     * Accepts the [fileName] of the directory and its immediate [children] and creates a new directory path.
     *
     * This constructor can be nested to define a tree of paths.
     */
    constructor(fileName: String, firstChild: MutableFSPath, vararg children: MutableFSPath) : super(fileName) {
        // The purpose of the [firstChild] parameter is to resolve ambiguity with the other constructors.
        addChildren(firstChild, *children)
    }

    /**
     * Returns whether the file represented by this path exists in the filesystem.
     *
     * @param checkType: Check not only whether the file exists, but also whether it is a directory.
     */
    override fun exists(checkType: Boolean): Boolean = if (checkType) toFile().isDirectory else toFile().exists()

    /**
     * Return a copy of this path.
     *
     * Children of this path are copied deeply.
     */
    override fun copy(): DirPath {
        val new = DirPath(*relativeSegments)
        new.parent = parent
        new.children = children.map { it.copy() }.toMutableSet()
        return new
    }

    override fun plus(other: FSPath): MutableFSPath {
        val new = other.copy()
        var current = new
        while (current.parent != null) {
            current.parent = current.parent?.copy()
            current = current.parent!!
        }
        current.parent = this
        return new
    }

    override fun walkChildren(): Sequence<MutableFSPath> {
        fun walk(node: DirPath): Sequence<MutableFSPath> {
            return node.children.asSequence().flatMap {
                if (it is DirPath) sequenceOf(it) + walk(it) else sequenceOf(it)
            }
        }

        return walk(this)
    }

    /**
     * Populate [children] with the given paths and set [parent] to this object for each of them.
     */
    fun addChildren(children: Iterable<MutableFSPath>) {
        children.forEach { it.parent = this }
        this.children.addAll(children)
    }

    /**
     * Populate [children] with the given paths and set [parent] to this object for each of them.
     */
    fun addChildren(vararg children: MutableFSPath) {
        addChildren(children.asIterable())
    }

    /**
     * Populate [children] with paths from the filesystem.
     *
     * This method reads the filesystem to get the list of files contained in the directory represented by this object.
     * It then creates path objects from those file paths and populates [children] with them.
     *
     * @throws [NotADirectoryException] This exception is thrown if the path represented by this object is not the path
     * of an accessible directory.
     */
    fun findChildren() {
        val dirChildren: Array<File>? = toFile().listFiles()
        dirChildren ?: throw NotADirectoryException(
            "cannot access children because the path is not an accessible directory")
        addChildren(dirChildren.map {
            when {
                it.isDirectory -> DirPath(it.toPath().fileName)
                else -> FilePath(it.toPath().fileName)
            }
        })
    }

    /**
     * Populate [descendants] with paths from the filesystem.
     *
     * This method calls [findChildren] recursively.
     *
     * @throws [NotADirectoryException] This exception is thrown if the path represented by this object is not the path
     * of an accessible directory.
     */
    fun findDescendants() {
        findChildren()
        children.forEach { (it as? DirPath)?.findDescendants() }
    }

    /**
     * Create a read-only view of this directory path.
     */
    fun toView(): DirPathView = DirPathView(this)
}

/**
 * A read-only view of a file path.
 *
 * Objects of this type present a dynamic view of the file path objects passed in. This means that when the path objects
 * are updated, this view reflects those changes. View objects cannot modify their corresponding path objects.
 */
class FilePathView(inner: FilePathBase) : FilePathBase by inner

/**
 * A read-only view of a directory path.
 *
 * Objects of this type present a dynamic view of the directory path objects passed in. This means that when the path
 * objects are updated, this view reflects those changes. View objects cannot modify their corresponding path objects.
 */
class DirPathView(inner: DirPathBase) : DirPathBase by inner

fun main(args: Array<String>) {
//    val directory1 = DirPath("/", "home", "garrett")
//    directory1.addChildren(
//        DirPath("Documents",
//            FilePath("Concepts Essay.odt"),
//            FilePath("Innovation Essay.odt")
//        ),
//        DirPath("Music",
//            DirPath("Popular Band",
//                FilePath("Song 1.mp3"),
//                FilePath("Song 2.mp3")
//            )
//        )
//    )
//
//    val directory2 = DirPath("/", "home", "garrett")
//    directory2.addChildren(
//        DirPath("Documents",
//            FilePath("Rhetoric Essay.odt"),
//            FilePath("Concepts Essay.odt")
//        ),
//        DirPath("Music",
//            DirPath("Popular Band",
//                FilePath("Song 1.mp3"),
//                FilePath("Song 3.mp3")
//            ),
//            DirPath("Obscure Band",
//                FilePath("Song 1.mp3")
//            )
//        )
//    )
}

/*
val directory = DirPath("home", "garrett")
directory.addChildren(
    DirPath("Documents",
        FilePath("Rhetoric Essay.odt"),
        FilePath("Concepts Essay.odt"),
        FilePath("Innovation Essay.odt"),
    ),
    DirPath("Music",
        DirPath("Popular Band",
            FilePath("Song 1.mp3"),
            FilePath("Song 2.mp3")
        ),
        DirPath("Obscure Band",
            FilePath("Song 1.mp3"),
            FilePath("Song 2.mp3")
        )
    )
)
*/
