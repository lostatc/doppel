package diffir

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.*

/**
 * A function that is called for each error that occurs during a filesystem operation. Functions of this type are passed
 * the file that caused the error and the exception that was thrown. They return a value which determines how that error
 * is handled.
 */
typealias ErrorHandler = (File, IOException) -> OnErrorAction

/**
 * Handle filesystem errors by always skipping the file that caused the error.
 */
@Suppress("UNUSED_PARAMETER")
fun skipOnError(file: File, exception: IOException): OnErrorAction = OnErrorAction.SKIP

/**
 * Handle filesystem errors by always terminating the operation when there is an error.
 */
@Suppress("UNUSED_PARAMETER")
fun terminateOnError(file: File, exception: IOException): OnErrorAction = OnErrorAction.TERMINATE

/**
 * Handle filesystem errors by always throwing the exception.
 */
@Suppress("UNUSED_PARAMETER")
fun throwOnError(file: File, exception: IOException): Nothing {
    throw exception
}

/**
 * The default error handler used by functions and classes which accept one.
 */
val DEFAULT_ERROR_HANDLER: ErrorHandler = ::skipOnError

/**
 * An exception used to terminate a move operation.
 */
private class TerminateException(file: File) : FileSystemException(file)

/**
 * Iterate through [walk] and execute [action] for each iteration while handling errors with [onError].
 *
 * @param [action] A function to execute for each iteration of [walk] that accepts the current file and returns whether
 * or not there was an error.
 *
 * @return `true` if there were no errors and `false` if there were.
 */
private fun walkWithErrorHandler(walk: FileTreeWalk, onError: ErrorHandler, action: (File) -> Boolean): Boolean {
    // Call the [onError] function if the file tree walk fails to read a directory's file list.
    walk.onFail { file, exception ->
        // An exception must be used to terminate the loop because we cannot break from inside a lambda.
        if (onError(file, exception) == OnErrorAction.TERMINATE) throw TerminateException(file)
    }

    return try {
        walk.all { path -> action(path) }
    } catch (e: TerminateException) {
        false
    }
}

/**
 * A change to apply to the filesystem.
 */
interface Action {
    /**
     * Applies the change to [dirPath].
     *
     * After this is called, [dirPath] should match what the filesystem would look like with the change applied assuming
     * there are no errors.
     */
    fun applyView(dirPath: MutableDirPath)

    /**
     * Applies the change to the filesystem.
     *
     * @return `true` if the action completed successfully or `false` if there were errors.
     */
    fun applyFilesystem(): Boolean
}

/**
 * An action that recursively moves a file or directory.
 *
 * @property [source] The file or directory to move.
 * @property [target] The file or directory to move [source] to.
 * @property [overwrite] If a file already exists at [target], replace it.
 * @property [onError] The function used to handle errors.
 *
 * [onError] can be passed the following exceptions:
 * - [NoSuchFileException]: There was an attempt to move a nonexistent file.
 * - [FileAlreadyExistsException]: The destination file already exists.
 * - [AccessDeniedException]: There was an attempt to open a directory that didn't succeed.
 * - [IOException]: Some other problem occurred while moving.
 */
class MoveAction(
    val source: FSPath, val target: FSPath,
    val overwrite: Boolean = false,
    val onError: ErrorHandler = DEFAULT_ERROR_HANDLER
) : Action {
    override fun applyView(dirPath: MutableDirPath) {
        if (source.startsWith(dirPath)) dirPath.descendants.remove(source)
        if (target.startsWith(dirPath)) dirPath.descendants.add(target as MutableFSPath)
    }

    override fun applyFilesystem(): Boolean {
        fun moveFile(sourceFile: File): Boolean {
            // Get the corresponding file in the target directory.
            val relativePath = sourceFile.toRelativeString(sourceFile)
            val destFile = File(target.toFile(), relativePath)

            // Call the [onError] function if the source file does not exist.
            if (!sourceFile.exists()) {
                val action = onError(
                    sourceFile,
                    NoSuchFileException(file = sourceFile, reason = "The source file doesn't exist.")
                )
                if (action == OnErrorAction.TERMINATE) return false
            }

            try {
                // Attempt to move the file.
                if (overwrite) {
                    Files.move(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                } else {
                    Files.move(sourceFile.toPath(), destFile.toPath())
                }
            } catch (e: IOException) {
                // Handle the exceptions thrown by [Files.move] or pass them to the [onError] function.
                val action = when (e) {
                    is DirectoryNotEmptyException -> return true
                    else -> onError(destFile, e)
                }

                if (action == OnErrorAction.TERMINATE) return false
            }

            return true
        }

        // Execute [moveFile] for each file in the source directory tree.
        return walkWithErrorHandler(source.toFile().walkTopDown(), onError, ::moveFile)
    }
}

/**
 * An action that recursively copies a file or directory.
 *
 * @property [source] The file or directory to copy.
 * @property [target] The file or directory to copy [source] to.
 * @property [overwrite] If a file already exists at [target], replace it.
 * @property [onError] The function used to handle errors.
 *
 * [onError] can be passed the following exceptions:
 * - [NoSuchFileException]: There was an attempt to copy a nonexistent file.
 * - [FileAlreadyExistsException]: The destination file already exists.
 * - [AccessDeniedException]: There was an attempt to open a directory that didn't succeed.
 * - [IOException]: Some other problem occurred while copying.
 */
class CopyAction(
    val source: FSPath, val target: FSPath,
    val overwrite: Boolean = false,
    val onError: ErrorHandler = DEFAULT_ERROR_HANDLER
) : Action {
    override fun applyView(dirPath: MutableDirPath) {
        if (target.startsWith(dirPath)) dirPath.descendants.add(target as MutableFSPath)
    }

    override fun applyFilesystem(): Boolean =
        source.toFile().copyRecursively(target.toFile(), overwrite, onError)
}

/**
 * An action that creates a new file.
 *
 * @property [path] The path of the new file.
 * @property [contents] A stream containing the data to fill the file with.
 * @property [onError] The function used to handle errors.
 *
 * [onError] can be passed the following exceptions:
 * - [FileAlreadyExistsException]: The file already exists.
 * - [IOException]: Some other problem occurred while creating the file.
 */
class CreateFileAction(
    val path: FilePath,
    val contents: InputStream = ByteArrayInputStream(ByteArray(0)),
    val onError: ErrorHandler = DEFAULT_ERROR_HANDLER
) : Action {
    override fun applyView(dirPath: MutableDirPath) {
        dirPath.descendants.add(path as MutableFSPath)
    }

    override fun applyFilesystem(): Boolean {
        if (path.exists()) {
            val action = onError(
                path.toFile(),
                FileAlreadyExistsException(file = path.toFile(), reason = "The file already exists.")
            )

            if (action == OnErrorAction.TERMINATE) return false
        }

        contents.use { input ->
            path.toFile().outputStream().use { output -> input.copyTo(output) }
        }

        return true
    }
}

/**
 * An action that creates a new directory and any necessary parent directories.
 *
 * @property [path] The path of the new directory.
 * @property [onError] The function used to handle errors.
 *
 * [onError] can be passed the following exceptions:
 * - [FileAlreadyExistsException]: The file already exists.
 * - [IOException]: Some other problem occurred while creating the directory.
 */
class CreateDirAction(val path: DirPath, val onError: ErrorHandler = DEFAULT_ERROR_HANDLER) : Action {
    override fun applyView(dirPath: MutableDirPath) {
        dirPath.descendants.add(path as MutableFSPath)
    }

    override fun applyFilesystem(): Boolean {
        if (path.exists()) {
            val action = onError(
                path.toFile(),
                FileAlreadyExistsException(file = path.toFile(), reason = "The file already exists.")
            )

            if (action == OnErrorAction.TERMINATE) return false
        }

        return path.toFile().mkdirs()
    }
}

/**
 * An action that recursively deletes a file or directory.
 *
 * @property [path] The path of the file or directory to delete.
 * @property [onError] The function used to handle errors.
 *
 * [onError] can be passed the following exceptions:
 * - [NoSuchFileException]: There was an attempt to delete a nonexistent file.
 * - [AccessDeniedException]: There was an attempt to open a directory that didn't succeed.
 * - [IOException]: Some other problem occurred while deleting.
 */
class DeleteAction(val path: FSPath, val onError: ErrorHandler = DEFAULT_ERROR_HANDLER) : Action {
    override fun applyView(dirPath: MutableDirPath) {
        dirPath.descendants.remove(path)
    }

    override fun applyFilesystem(): Boolean {
        fun deleteFile(deletePath: File): Boolean {
            try {
                Files.delete(deletePath.toPath())
            } catch (e: IOException) {
                val action = onError(deletePath, e)
                if (action == OnErrorAction.TERMINATE) return false
            }

            return true
        }

        // Execute [deleteFile] for each file in the directory tree.
        return walkWithErrorHandler(path.toFile().walkBottomUp(), onError, ::deleteFile)
    }
}

/**
 * A set of changes to apply to the filesystem.
 *
 * This class allows for creating a set of changes that can be applied to multiple directories. Changes are stored in a
 * queue and can be applied to the filesystem all at once. New changes can be queued up by passing an instance of
 * [Action] to the [add] method, but the filesystem is not modified until [apply] is called. There are actions for
 * moving, copying, creating and deleting files and directories. Custom actions can be created by subclassing [Action].
 *
 * The [getExpected] method can be used to see what a directory will look like after all changes are applied.
 */
class PathDelta {
    /**
     * A list of actions to apply to the filesystem.
     */
    private val actions: Deque<Action> = LinkedList()

    /**
     * Adds a change to the queue.
     *
     * This change is not applied to the filesystem until the [apply] method is called.
     */
    fun add(change: Action) {
        actions.addLast(change)
    }

    /**
     * Undoes the given number of [changes] that have been queued up and returns how many were undone.
     *
     * @throws [IllegalArgumentException] The given number of changes is negative.
     */
    fun undo(changes: Int): Int {
        require(changes < 0) { "the given number of changes must be positive" }

        val startSize = actions.size
        repeat(changes) { actions.pollLast() }
        return startSize - actions.size
    }

    /**
     * Clears all changes that have been queued up and returns how many were cleared.
     */
    fun clear(): Int {
        val numActions = actions.size
        actions.clear()
        return numActions
    }

    /**
     * Returns what [dirPath] will look like after the [apply] method is called assuming there are no errors.
     */
    fun getExpected(dirPath: DirPath): DirPath {
        val outputPath = dirPath.toMutableDirPath()
        for (action in actions) {
            action.applyView(outputPath)
        }
        return outputPath
    }

    /**
     * Applies the changes in this delta to the filesystem in the order they were made.
     *
     * Applying the changes does not consume them. If any [ErrorHandler] throws an exception, it will be thrown here.
     */
    fun apply() {
        for (action in actions) {
            action.applyFilesystem()
        }
    }
}
