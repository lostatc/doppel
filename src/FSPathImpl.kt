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
 * @constructor Accepts the segments of a file or directory path without path separators and creates a new path. The
 * last segment will become this path's [fileName], and rest of them will become this path's parent and ancestors.
 */
abstract class MutableFSPath protected constructor(segments: List<String>) : FSPath, SimpleObservable {
    final override val fileName: String = segments.last()

    final override var parent: DirPath? =
        with(segments) { if (size > 1) DirPath.of(*dropLast(1).toTypedArray()) else null }
        set(value) {
            if (containsRoot && value != null) {
                throw IsAbsolutePathException("absolute paths cannot have a parent")
            } else {
                val oldValue = field
                field = value
                notify(::parent, oldValue, value)
            }
        }

    override val observers: MutableList<SimpleObserver> = mutableListOf()

    /**
     * This path, excluding parents, is absolute.
     */
    private val containsRoot: Boolean
        get() = Paths.get(fileName).isAbsolute

    override fun toString(): String = toPath().toString()

    override fun equals(other: Any?): Boolean =
        if (other is FSPath && this::class == other::class) pathSegments == other.pathSegments else false

    override fun hashCode(): Int = pathSegments.hashCode()

    /**
     * Set the [parent] property to [newParent] and add this path as a child of [newParent].
     *
     * @return `true` if the parent was successfully changed and `false` if it was already equal to [newParent].
     */
    fun addParent(newParent: DirPath): Boolean {
        if (parent == newParent) return false
        newParent.children.add(this)
        parent = newParent
        return true
    }

    /**
     * Set the [parent] property to `null` and remove this path from the children of [parent].
     *
     * @return `true` if the parent was successfully changed and `false` if it was already `null`.
     */
    fun removeParent(): Boolean {
        parent?.children?.remove(this) ?: return false
        parent = null
        return true
    }

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
class FilePath private constructor(segments: List<String>) : MutableFSPath(segments), FilePathBase {
    /**
     * Returns whether the file represented by this path exists in the filesystem.
     *
     * @param checkType: Check not only whether the file exists, but also whether it is a normal file.
     */
    override fun exists(checkType: Boolean): Boolean = if (checkType) toFile().isFile else toFile().exists()

    override fun copy(): FilePath {
        val new = of(fileName)
        new.parent = parent
        return new
    }

    /**
     * Create a read-only view of this file path.
     */
    fun asView(): FilePathView = FilePathView(this)

    companion object {
        private fun valueOf(segments: List<String>): FilePath {
            val path = FilePath(segments)
            path.parent?.children?.add(path)
            return path
        }

        /**
         * Construct a new file path from the given path [segments] without path separators.
         *
         * This returns a hierarchy of [MutableFSPath] objects where the last segment becomes the new path's [fileName],
         * and the rest of them become the path's parent and ancestors.
         */
        fun of(vararg segments: String): FilePath = valueOf(segments.toList())

        /**
         * Construct a new file path from the segments of the given [path].
         *
         * This returns a hierarchy of [MutableFSPath] objects where the last segment becomes the new path's [fileName],
         * and the rest of them become the path's parent and ancestors.
         */
        fun of(path: Path): FilePath = valueOf(path.map { it.toString() })
    }
}

/**
 * A mutable representation of a directory path.
 */
class DirPath private constructor(segments: List<String>) : MutableFSPath(segments), DirPathBase {
    /**
     * A mutable representation of the paths of the immediate children of the directory.
     *
     * This set is automatically updated whenever one of the paths contained in it changes. It is safe for the contents
     * of this set to be mutated.
     */
    override var children: MutableSet<MutableFSPath> = UpdatableSet<MutableFSPath>()

    /**
     *
     */

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
        val new = of(fileName)
        new.children = UpdatableSet(children.map { it.copy() })
        new.parent = parent
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
     * Add [newChildren] to [children] and set [parent] to this path for each of them.
     *
     * @return `true` if any of the specified paths were added to [children], `false` if [children] was not modified.
     */
    fun addChildren(newChildren: Collection<MutableFSPath>): Boolean {
        newChildren.forEach { it.parent = this }
        return children.addAll(newChildren)
    }

    /**
     * Add [newChildren] to [children] and set [parent] to this path for each of them.
     *
     * @return `true` if any of the specified paths were added to [children], `false` if [children] was not modified.
     */
    fun addChildren(vararg newChildren: MutableFSPath): Boolean = addChildren(newChildren.toList())

    /**
     * Remove [existingChildren] from [children] and set [parent] to `null` for each fo them.
     *
     * @return `true` if any of the specified paths were removed from [children], `false` if [children] was not
     * modified.
     */
    fun removeChildren(existingChildren: Collection<MutableFSPath>): Boolean {
        children.filter { it in existingChildren }.forEach { it.parent = null }
        return children.removeAll(existingChildren)
    }

    /**
     * Remove [existingChildren] from [children] and set [parent] to `null` for each fo them.
     *
     * @return `true` if any of the specified paths were removed from [children], `false` if [children] was not
     * modified.
     */
    fun removeChildren(vararg existingChildren: MutableFSPath): Boolean = removeChildren(existingChildren.toList())

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
                it.isDirectory -> DirPath.of(it.toPath().fileName)
                else -> FilePath.of(it.toPath().fileName)
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
    fun asView(): DirPathView = DirPathView(this)

    companion object {
        private fun valueOf(segments: List<String>): DirPath {
            val path = DirPath(segments)
            path.parent?.children?.add(path)
            return path
        }

        /**
         * Construct a new directory path from the given path [segments] without path separators.
         *
         * This returns a hierarchy of [MutableFSPath] objects where the last segment becomes the new path's [fileName],
         * and the rest of them become the path's parent and ancestors.
         */
        fun of(vararg segments: String): DirPath = valueOf(segments.toList())

        /**
         * Construct a new directory path from the segments of the given [path].
         *
         * This returns a hierarchy of [MutableFSPath] objects where the last segment becomes the new path's [fileName],
         * and the rest of them become the path's parent and ancestors.
         */
        fun of(path: Path): DirPath = valueOf(path.map { it.toString() })

        /**
         * Construct a new directory path from the [fileName] of the directory and a list of immediate [children] to
         * initialize it with starting with [firstChild].
         *
         * This factory method can be nested to define a tree of paths.
         */
        fun of(fileName: String, firstChild: MutableFSPath, vararg children: MutableFSPath): DirPath {
            // The purpose of the [firstChild] parameter is to disambiguate this factory method from the others so that
            // an instance can be created by passing in a single string.
            val path = of(fileName)
            path.addChildren(firstChild, *children)
            return path
        }
    }
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
