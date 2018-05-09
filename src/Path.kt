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
 * The path of a file or directory.
 *
 * @constructor Accepts the [segments] of a file or directory path without path separators and creates a new path.
 */
abstract class FSPath(protected vararg val segments: String) {
    /**
     * A list containing the segments of the path without path separators.
     */
    val pathSegments: List<String>
        get() = (parent?.pathSegments ?: listOf<String>()) + segments

    /**
     * The parent path. Null if there is no parent.
     *
     * @throws [IsAbsolutePathException] This exception is thrown if the property is set to a non-null value while
     * [pathSegments] is absolute.
     */
    var parent: FSPath? = null
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
    protected val containsRoot: Boolean
        get() = Paths.get("", *segments).isAbsolute

    /**
     * Creates a new path from the given [path].
     */
    constructor(path: Path) : this(*path.map { it.toString() }.toTypedArray())

    /**
     * Returns a [Path] representing this path.
     */
    fun toPath(): Path = Paths.get("", *pathSegments.toTypedArray())

    /**
     * Returns a [File] representing this path.
     */
    fun toFile(): File = toPath().toFile()

    /**
     * Returns the string representation of this path.
     */
    override fun toString(): String = toPath().toString()

    /**
     * Indicates wither the object [other] is equal to this one.
     *
     * This path and [other] are equal if they are the same type and if their [pathSegments] properties are equal.
     */
    override operator fun equals(other: Any?) =
        if (other is FSPath && this::class == other::class) pathSegments == other.pathSegments else false

    /**
     * Return a copy of [other] with this as the ancestor.
     *
     * This method climbs the tree of parents until it finds a path whose parent is null. It then makes this that path's
     * parent.
     *
     * @throws [IsAbsolutePathException] This exception is thrown if [other] is an absolute path.
     */
    operator fun plus(other: FSPath): FSPath {
        val new = other.copy()
        var current = new
        while (current.parent != null) {
            current.parent = current.parent?.copy()
            current = current.parent!!
        }
        current.parent = this
        return new
    }

    /**
     * Return a hash code value for the object.
     */
    override fun hashCode(): Int = pathSegments.hashCode()

    /**
     * Return a copy of this path.
     */
    abstract fun copy(): FSPath

    /**
     * Return a copy of this path which is relative to [ancestor].
     *
     * This method climbs the tree of parents until it finds the path whose parent is [ancestor]. It then sets that
     * path's parent to null.
     */
    fun relativeTo(ancestor: FSPath): FSPath {
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
 * The path of a file.
 */
class FilePath : FSPath {
    constructor(vararg segments: String) : super(*segments)
    constructor(path: Path) : super(path)

    /**
     * Return a copy of this path.
     */
    override fun copy(): FilePath {
        val new = FilePath(*segments)
        new.parent = parent
        return new
    }
}

/**
 * The path of a directory.
 */
class DirPath : FSPath {
    /**
     * The paths of the immediate children of the directory.
     */
    var children: MutableSet<FSPath> = mutableSetOf()

    /**
     * The paths of all descendants of the directory.
     */
    val descendants: Set<FSPath>
        get() = walkChildren().toSet()

    constructor(vararg segments: String) : super(*segments)

    constructor(path: Path) : super(path)

    /**
     * Accepts the [fileName] of the directory and its immediate [children] and creates a new directory path.
     *
     * This constructor can be nested to define a tree of paths.
     */
    constructor(fileName: String, firstChild: FSPath, vararg children: FSPath) : super(fileName) {
        // The purpose of the [firstChild] parameter is to resolve ambiguity with the other constructors.
        addChildren(firstChild, *children)
    }

    private fun walkChildren(node: DirPath = this): List<FSPath> {
        return node.children.flatMap {
            listOf(it) + if (it is DirPath) walkChildren(it) else listOf()
        }
    }

    /**
     * Return a copy of this path.
     *
     * Children of this path are copied deeply.
     */
    override fun copy(): DirPath {
        val new = DirPath(*segments)
        new.parent = parent
        new.children = children.map { it.copy() }.toMutableSet()
        return new
    }

    /**
     * Populate [children] with the given paths and set [parent] to this object for each of them.
     */
    fun addChildren(children: Iterable<FSPath>) {
        children.forEach { it.parent = this }
        this.children.addAll(children)
    }

    /**
     * Populate [children] with the given paths and set [parent] to this object for each of them.
     */
    fun addChildren(vararg children: FSPath) {
        addChildren(children.asIterable())
    }

    /**
     * Populate [children] with paths from the filesystem.
     *
     * This method reads the filesystem to get the list of files contained in the directory represented by this object.
     * It then creates [FSPath] objects from those file paths and populates [children] with them.
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

    infix fun diff(other: DirPath) = PathDiff(this, other)
}

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
//
//    val copy = directory2.copy()
//    println(directory2.descendants)
//    println(copy.descendants)
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
