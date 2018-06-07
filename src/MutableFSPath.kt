package diffir

import java.nio.file.Path
import java.nio.file.Paths
import java.io.File
import java.io.IOException
import kotlin.reflect.KProperty

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
    .map { it.withAncestor(directory) }
    .map { if (it is MutableDirPath) scanDescendants(it) + it else listOf(it) }
    .flatten()

/**
 * A mutable representation of a file or directory path.
 *
 * This class contains properties and methods common to all mutable paths.
 */
abstract class MutableFSPath protected constructor(segments: List<String>) : FSPath, SimpleObservable {
    // This is final to prevent subclasses from making it var, which could cause problems unless it was also made
    // observable since it is used in [equals] and [hashCode].
    final override val fileName: String = segments.last()

    /**
     * The backing property of [parent].
     *
     * This is used to set the parent without affecting the parent's children.
     */
    internal var _parent: MutableDirPath? = with(segments) { if (size > 1) MutableDirPath(dropLast(1)) else null }
        set(value) {
            if (containsRoot && value != null) {
                throw IllegalArgumentException("parent cannot be non-null if this path is a filesystem root")
            } else {
                val oldValue = field
                field = value
                notify(::parent, oldValue, value)
            }
        }

    /**
     * The parent path. Null if there is no parent.
     *
     * If set to a non-null value, this path is added to the children of the new parent. If set to `null`, this path is
     * removed from the children of the old parent.
     *
     * @throws [IllegalArgumentException] This exception is thrown if the property is set to a non-null value while
     * [fileName] is a filesystem root.
     */
    override var parent: MutableDirPath?
        get() = _parent
        set(value) {
            value?._children?.add(this) ?: _parent?._children?.remove(this)
            _parent = value
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

    abstract override fun copy(): MutableFSPath

    override fun relativeTo(ancestor: DirPath): MutableFSPath {
        if (!startsWith(ancestor))
            throw IllegalArgumentException("the given path must be an ancestor of this path")

        val new = copy()
        var current = new
        while (current.parent != ancestor && current.parent != null) {
            current._parent = current.parent?.copy()
            current = current.parent ?: break
        }
        current._parent = null
        return new
    }

    override fun withAncestor(ancestor: DirPath): MutableFSPath {
        val new = copy()
        var current = new
        while (current.parent != null) {
            current._parent = current.parent?.copy()
            current = current.parent ?: break
        }
        current._parent = ancestor.toMutableDirPath()
        return new
    }
}

/**
 * A mutable representation of a file path.
 *
 * To create new instances of this class, see the factory method [invoke]. Creating an instance with this method uses
 * the same syntax as using a constructor.
 */
class MutableFilePath private constructor(segments: List<String>) : MutableFSPath(segments), FilePath {
    /**
     * Returns whether the file represented by this path exists in the filesystem.
     *
     * @param [checkType] Check not only whether the file exists, but also whether it is a normal file.
     */
    override fun exists(checkType: Boolean): Boolean = if (checkType) toFile().isFile else toFile().exists()

    override fun copy(): MutableFilePath {
        val new = invoke(fileName)
        new._parent = parent
        return new
    }

    override fun relativeTo(ancestor: DirPath): MutableFilePath = super.relativeTo(ancestor) as MutableFilePath

    override fun withAncestor(ancestor: DirPath): MutableFilePath = super.withAncestor(ancestor) as MutableFilePath

    companion object {
        /**
         * Constructs a new file path from the given path [segments] without path separators.
         *
         * @return A hierarchy of [MutableFSPath] objects where the last segment becomes the new path's [fileName], and
         * the rest of them become the new path's parent and ancestors.
         */
        operator fun invoke(segments: List<String>): MutableFilePath {
            val path = MutableFilePath(segments)
            path.parent?._children?.add(path)
            return path
        }

        /**
         * Constructs a new file path from the given path segments without path separators.
         *
         * @return A hierarchy of [MutableFSPath] objects where the last segment becomes the new path's [fileName], and
         * the rest of them become the new path's parent and ancestors.
         */
        operator fun invoke(firstSegment: String, vararg segments: String): MutableFilePath =
            invoke(listOf(firstSegment, *segments))

        /**
         * Constructs a new file path from the segments of the given [path].
         *
         * @return A hierarchy of [MutableFSPath] objects where the last segment becomes the new path's [fileName], and
         * the rest of them become the new path's parent and ancestors.
         */
        operator fun invoke(path: Path): MutableFilePath = invoke(path.map { it.toString() })
    }
}

/**
 * A mutable representation of a directory path.
 *
 * To create new instances of this class, see the factory method [invoke]. Creating an instance with this method uses
 * the same syntax as using a constructor.
 */
class MutableDirPath private constructor(segments: List<String>) : MutableFSPath(segments), DirPath {
    /**
     * The backing property of [children].
     *
     * This is used to modify the children of the directory without affecting their parents.
     */
    internal val _children: MutableSet<MutableFSPath> = UpdatableSet<MutableFSPath>()

    /**
     * A mutable representation of the paths of the immediate children of the directory.
     *
     * This set is automatically updated whenever one of the paths contained in it changes. It is safe for the paths
     * contained in this set to be modified.
     *
     * Whenever children are added to this set, the [parent] property of each of them is set to this directory. Whenever
     * children are removed from this set, the [parent] property of each of them is set to `null`.
     */
    override val children: MutableSet<MutableFSPath> = PathChildren(this, _children)


    /**
     * A mutable representation of the paths of all descendants of the directory.
     *
     * This set is automatically updated whenever one of the paths contained in it changes. It is safe for the paths
     * contained in this set to be modified. Changes to any paths in the tree are reflected in this set.
     *
     * Items added to the set are inserted into their proper location in the tree. Both relative and absolute paths can
     * be added to the set. Relative paths are considered to be relative to this directory, and absolute paths must
     * start with this directory. Items which are removed from the set are removed from their location in the tree.
     */
    override val descendants: MutableSet<MutableFSPath> = PathDescendants(this)

    /**
     * Notifies each of the [observers] that this object has changed.
     *
     * This method also notifies the observers of all of its [descendants]. Observers are notified in the order in which
     * they appear in [observers].
     */
    override fun <T> notify(property: KProperty<*>, oldValue: T, newValue: T) {
        // Create a copy to avoid a ConcurrentModificationException.
        children.toList().forEach { it.notify(property, oldValue, newValue) }
        super.notify(property, oldValue, newValue)
    }

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
    override fun copy(): MutableDirPath {
        val new = invoke(fileName)
        new.children.addAll(children.map { it.copy() })
        new._parent = parent
        return new
    }

    override fun relativeTo(ancestor: DirPath): MutableDirPath = super.relativeTo(ancestor) as MutableDirPath

    override fun withAncestor(ancestor: DirPath): MutableDirPath = super.withAncestor(ancestor) as MutableDirPath

    override fun walkChildren(): Sequence<MutableFSPath> = super.walkChildren().map { it as MutableFSPath }

    /**
     * Adds [newChildren] to [children].
     *
     * This is a vararg shortcut that calls [children.addAll][MutableSet.addAll].
     *
     * @return `true` if any of the specified paths were added to [children], `false` if [children] was not modified.
     */
    fun addChildren(vararg newChildren: MutableFSPath): Boolean = children.addAll(newChildren.toList())

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

    companion object {
        /**
         * Constructs a new directory path from the given path [segments] without path separators.
         *
         * @return A hierarchy of [MutableFSPath] objects where the last segment becomes the new path's [fileName], and
         * the rest of them become the new path's parent and ancestors.
         */
        operator fun invoke(segments: List<String>): MutableDirPath {
            val path = MutableDirPath(segments)
            path.parent?._children?.add(path)
            return path
        }

        /**
         * Constructs a new directory path from the given path segments without path separators.
         *
         * @return A hierarchy of [MutableFSPath] objects where the last segment becomes the new path's [fileName], and
         * the rest of them become the new path's parent and ancestors.
         */
        operator fun invoke(firstSegment: String, vararg segments: String): MutableDirPath =
            invoke(listOf(firstSegment, *segments))

        /**
         * Constructs a new directory path from the segments of the given [path].
         *
         * @return A hierarchy of [MutableFSPath] objects where the last segment becomes the new path's [fileName], and
         * the rest of them become the new path's parent and ancestors.
         */
        operator fun invoke(path: Path): MutableDirPath = invoke(path.map { it.toString() })

        /**
         * Constructs a new directory path from the [fileName] of the directory and a list of immediate [children] to
         * initialize it with starting with [firstChild].
         *
         * This factory method can be nested to define a tree of paths.
         *
         * @return A new directory path containing the given children.
         */
        operator fun invoke(fileName: String, firstChild: MutableFSPath, vararg children: MutableFSPath): MutableDirPath {
            // The purpose of the [firstChild] parameter is to disambiguate this factory method from the others so that
            // an instance can be created by passing in a single string.
            val path = invoke(listOf(fileName))
            path.children.addAll(listOf(firstChild, *children))
            return path
        }
    }
}
