package diffir

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Possible orders in which hierarchical data can be iterated over.
 */
enum class WalkDirection {
    /**
     * The data is iterated over from the trunk to the leaves.
     */
    TOP_DOWN,

    /**
     * The data is iterated over from the leaves to the trunk.
     */
    BOTTOM_UP
}

/**
 * A read-only representation of a file or directory path.
 *
 * [FSPath] objects wrap a [Path] object to allow them to form a tree of file and directory paths. This allows file
 * hierarchies to be represented and manipulated in memory. Every [FSPath] object has a [fileName] property representing
 * the name of the current file and a [parent] property that points to another [FSPath] object. A [Path] object
 * representing the path can be accessed through the [path] property.
 */
interface FSPath {
    /**
     * The name of the file or directory represented by this path.
     */
    val fileName: Path

    /**
     * The parent path or `null` if there is no parent.
     */
    val parent: DirPath?

    /**
     * The ancestor whose [parent] is `null`. This could be this path.
     */
    val root: FSPath

    /**
     * A [Path] representing this path.
     *
     * This is computed using [fileName] and [parent].
     */
    val path: Path
        get() = parent?.path?.resolve(fileName) ?: fileName

    /**
     * Returns the string representation of this path.
     */
    override fun toString(): String

    /**
     * Indicates wither the object [other] is equal to this one.
     *
     * This path and [other] are equal if they are the same type and their [path] and [parent] properties are equal.
     */
    override operator fun equals(other: Any?): Boolean

    /**
     * Returns a hash code value for the object.
     *
     * The hash code value is based on the [path] and [parent] properties.
     */
    override fun hashCode(): Int

    /**
     * Returns whether the file represented by this path exists in the filesystem.
     *
     * @param [checkType] Check not only whether the file exists, but also whether the type of file matches the type of
     * the the object.
     */
    fun exists(checkType: Boolean = true): Boolean

    /**
     * Returns a copy of this path.
     *
     * @param [fileName] The new file name to assign to the copy.
     * @param [parent] The new parent to assign to the copy.
     */
    fun copy(fileName: Path = this.fileName, parent: DirPath? = this.parent): FSPath

    /**
     * Returns a sequence of all the ancestors of this directory path.
     *
     * @param [direction] The direction in which to iterate over ancestors.
     */
    fun walkAncestors(direction: WalkDirection = WalkDirection.BOTTOM_UP): Sequence<DirPath>

    /**
     * Returns whether this path starts with the path [other].
     *
     * @see [Path.startsWith]
     */
    fun startsWith(other: FSPath): Boolean = path.startsWith(other.path)

    /**
     * Returns whether this path ends with the path [other].
     *
     * @see [Path.endsWith]
     */
    fun endsWith(other: FSPath): Boolean = path.endsWith(other.path)
}

/**
 * A read-only representation of a file path.
 */
interface FilePath : FSPath {
    override fun copy(fileName: Path, parent: DirPath?): FilePath

    /**
     * Returns a copy of this path as a mutable file path.
     */
    fun toMutableFilePath(): MutableFilePath = copy() as MutableFilePath
}

/**
 * A read-only representation of a directory path.
 */
interface DirPath : FSPath {
    /**
     * The paths of the immediate children of the directory.
     */
    val children: Set<FSPath>

    /**
     * The paths of all the descendants of the directory.
     */
    val descendants: Set<FSPath>

    override fun copy(fileName: Path, parent: DirPath?): DirPath

    /**
     * Returns a copy of [other] which is relative to this path.
     *
     * If this path is "/a/b" and [other] is "/a/b/c/d", then the resulting path will be "c/d".
     *
     * If this is an absolute path, then [other] must be an an absolute path. Similarly, if this is a relative path,
     * then [other] must also be a relative path.
     *
     * @throws [IllegalArgumentException] [other] is not a path that can be relativized against this path.
     */
    fun <T : FSPath> relativize(other: T): T

    /**
     * Returns a copy of [other] with this path as its ancestor.
     *
     * If this path is "/a/b", and [other] is "c/d", then the resulting path will be "/a/b/c/d".
     *
     * If [other] is absolute, then this method returns [other].
     */
    fun <T : FSPath> resolve(other: T): T

    /**
     * Returns a sequence of all the descendants of this directory path.
     *
     * This walks through the tree of [children] depth-first regardless of which [direction] is being used. This path is
     * not included in the output.
     *
     * @param [direction] The direction in which to walk the tree.
     */
    fun walkChildren(direction: WalkDirection = WalkDirection.TOP_DOWN): Sequence<FSPath>

    /**
     * Returns whether every path in the tree exists in the filesystem.
     *
     * @param [checkType] Check not only whether each file exists, but also whether the type of file matches the type of
     * the object.
     */
    fun treeExists(checkType: Boolean = true): Boolean =
        exists(checkType) && walkChildren().all { it.exists(checkType) }

    /**
     * Returns an immutable representation of the difference between this directory and [other].
     *
     * @param [onError] A function that is called for each I/O error that occurs and determines how to handle them.
     *
     * The following exceptions can be passed to [onError]:
     * - [NoSuchFileException] A file in one of the directories was not found in the filesystem.
     * - [IOException]: Some other I/O error occurred.
     */
    fun diff(other: DirPath, onError: ErrorHandler = ::skipOnError): PathDiff {
        // Get the descendants of the directories as relative paths.
        val leftRelativeDescendants = this.descendants.asSequence().map { this.relativize(it) }.toSet()
        val rightRelativeDescendants = other.descendants.asSequence().map { other.relativize(it) }.toSet()

        // Compare file paths.
        val common = leftRelativeDescendants intersect rightRelativeDescendants
        val leftOnly = leftRelativeDescendants - rightRelativeDescendants
        val rightOnly = rightRelativeDescendants - leftRelativeDescendants

        val same = mutableSetOf<FSPath>()
        val different = mutableSetOf<FSPath>()
        val leftNewer =  mutableSetOf<FSPath>()
        val rightNewer =  mutableSetOf<FSPath>()

        // Compare files in the filesystem.
        common@ for (commonPath in common) {
            try {
                // Compare the contents of files in the directories.
                val filesAreTheSame = when(commonPath) {
                    is DirPath -> resolve(commonPath).children == other.resolve(commonPath).children
                    else -> compareContents(resolve(commonPath).path, other.resolve(commonPath).path)
                }

                if (filesAreTheSame) same.add(commonPath) else different.add(commonPath)

                // Compare the times of files in the directories.
                val leftTime = Files.getLastModifiedTime(resolve(commonPath).path)
                val rightTime = Files.getLastModifiedTime(other.resolve(commonPath).path)

                if (leftTime > rightTime) leftNewer.add(commonPath) else rightNewer.add(commonPath)

            } catch (e: IOException) {
                when (onError(commonPath.path, e)) {
                    OnErrorAction.SKIP -> continue@common
                    OnErrorAction.TERMINATE -> break@common
                }
            }
        }

        return PathDiff(
            this, other,
            common = common,
            leftOnly = leftOnly, rightOnly = rightOnly,
            same = same, different = different,
            leftNewer = leftNewer, rightNewer = rightNewer
        )
    }

    /**
     * Returns a copy of this path as a mutable directory path.
     */
    fun toMutableDirPath(): MutableDirPath = copy() as MutableDirPath
}
