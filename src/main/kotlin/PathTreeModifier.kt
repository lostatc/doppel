package diffir

import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.util.Deque
import java.util.LinkedList

/**
 * A function that moves/renames a file.
 */
typealias FileMoveFunc = (File, File) -> Unit

/**
 * A function that creates a file and fills it with data from an input stream.
 */
typealias FileCreateFunc = (File, InputStream) -> Unit

/**
 * A function that removes a file from the filesystem.
 */
typealias FileDeleteFunc = (File) -> Unit

/**
 * A function that modifies a set of paths.
 */
private typealias ViewAction = (MutableSet<MutableFSPath>) -> Unit

/**
 * A function that modifies the filesystem.
 */
private typealias FilesystemAction = () -> Unit

/**
 * Moves a file [sourceFile] in the filesystem to [destFile].
 */
private fun moveFile(sourceFile: File, destFile: File) {

}

/**
 * Creates a [file] in the filesystem with the given [contents].
 */
private fun createFile(file: File, contents: InputStream) {

}

/**
 * Deletes a [file] from the filesystem..
 */
private fun deleteFile(file: File) {

}

/**
 * Returns a tree of paths of the descendants of [directory] in the filesystem.
 */
private fun scanTree(directory: DirPath): List<MutableFSPath> {
    val children = scanChildren(directory).map { it.withAncestor(directory) }
    children
        .filterIsInstance<MutableDirPath>()
        .forEach { it.children.addAll(scanTree(it)) }
    return children
}

/**
 * A representation of the changes made to a directory tree.
 *
 * This class accepts the path of a directory that exists in the filesystem and provides methods for applying changes to
 * it. It stores two directory trees, one representing the current state of the filesystem and one representing what it
 * will look like once all changes are applied. The changes that are made to the virtual directory tree are queued up
 * and can be applied to the filesystem all at once.
 *
 * @param [existingPath] The existing directory tree to be modified.
 */
class PathTreeModifier(
    existingPath: DirPath,
    private val moveFunc: FileMoveFunc = ::moveFile,
    private val createFunc: FileCreateFunc = ::createFile,
    private val deleteFunc: FileDeleteFunc = ::deleteFile
) {
    init {
        if (!existingPath.exists())
            throw IllegalArgumentException("the given directory path must be the path of an existing directory")
    }

    /**
     * A change to apply to the filesystem.
     *
     * @property [viewFunc] A function that modifies [MutableDirPath.descendants] on [before] to create [after].
     * @property [filesystemFunc] A function that modifies the filesystem.
     */
    private data class Action(val viewFunc: ViewAction, val filesystemFunc: FilesystemAction)

    /**
     * A list of actions to apply to the filesystem.
     */
    private val actions: Deque<Action> = LinkedList()

    /**
     * A view of the original directory tree before any changes. This updates with the filesystem.
     */
    val before: DirPath = object : DirPath by existingPath {
        override val children: Set<FSPath> = ViewSet { scanTree(this).toSet() }

        override fun walkChildren(): Sequence<FSPath> = super.walkChildren()

        override val descendants: Set<FSPath> = super.walkChildren().toSet()
    }

    /**
     * A view of what the directory tree would look like with changes applied. This updates with the filesystem and with
     * any changes that are queued up.
     */
    val after: DirPath = object : DirPath by before {
        override val children: Set<FSPath> = ViewSet {
            val mutableBefore = before.toMutableDirPath()
            for ((viewFunc, _) in actions) {
                viewFunc(mutableBefore.descendants)
            }
            mutableBefore.children
        }
    }

    /**
     * A representation of the difference between the two directories. The left tree is [before] and the right tree is
     * [after].
     */
    val diff: PathDiff = before diff after

    /**
     * Moves a file into or out of [after] or renames a file in [after].
     *
     * @param [sourcePath] The path of the file to move.
     * @param [destPath] The path to move the file to.
     *
     * @throws [IllegalArgumentException] This exception is thrown if neither [sourcePath] nor [destPath] are
     * descendants of the path [after].
     */
    fun move(sourcePath: FSPath, destPath: FSPath) {
        if (!sourcePath.startsWith(after) && !destPath.startsWith(after))
            throw IllegalArgumentException("one of the given paths must be a descendant of the directory")

        actions.addLast(Action(
            {
                it.remove(sourcePath)
                if (destPath.startsWith(after)) it.add(destPath as MutableFSPath)
            },
            { moveFunc(sourcePath.toFile(), destPath.toFile()) }
        ))
    }

    /**
     * Creates a new file at [path] in [after] containing data from [contents].
     *
     * @throws [IllegalArgumentException] This exception is thrown when [path] is not a descendant of [after] or when
     * [path] already exists in [after].
     */
    fun create(path: FSPath, contents: InputStream) {
        if (!path.startsWith(after))
            throw IllegalArgumentException("the given path must be a descendant of the directory")
        if (path in after.descendants)
            throw IllegalArgumentException("the given path must not already exist in the directory")

        actions.addLast(Action(
            { it.add(path as MutableFSPath) },
            { createFunc(path.toFile(), contents) }
        ))

    }

    /**
     * Deletes the file at [path] from [after].
     *
     * @throws [IllegalArgumentException] This exception is thrown when [path] is not a descendant of [after] or when
     * [path] does not exist in [after].
     */
    fun delete(path: FSPath) {
        if (!path.startsWith(after))
            throw IllegalArgumentException("the given path must be a descendant of the directory")
        if (path !in after.descendants)
            throw IllegalArgumentException("the given path must exist in the directory")

        actions.addLast(Action(
            { it.remove(path) },
            { deleteFunc(path.toFile()) }
        ))

    }

    /**
     * Undo the given [number] of changes and return how many were undone.
     */
    fun undo(number: Int): Int {
        if (number < 0)
            throw IllegalArgumentException("the given number must be positive")

        var total = 0
        for (i in 1..number) {
            if (actions.pollLast() != null) total += 1
        }

        return total
    }

    /**
     * Applies the changes to the filesystem.
     */
    fun apply() {
        while (!actions.isEmpty()) {
            val (_, filesystemFunc) = actions.removeFirst()
            filesystemFunc()
        }
    }
}