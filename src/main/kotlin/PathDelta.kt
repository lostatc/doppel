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
 * A function that modifies a directory path.
 */
private typealias ViewAction = (MutableDirPath) -> Unit

/**
 * A function that modifies the filesystem.
 */
private typealias FilesystemAction = () -> Boolean

/**
 * A function that is called for each error that occurs during a filesystem operation. Functions of this type are passed
 * the file that caused the error and the exception that was thrown. They return a value which determines how that error
 * is handled.
 */
typealias ErrorHandler = (File, IOException) -> OnErrorAction

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
 * Moves a file in the filesystem from [source] to [target].
 *
 * @return `true` if the file was moved successfully and `false` otherwise.
 */
private fun moveRecursively(
        source: File, target: File,
        overwrite: Boolean, onError: ErrorHandler
): Boolean {
    fun moveFile(sourceFile: File): Boolean {
        // Get the corresponding file in the target directory.
        val relativePath = sourceFile.toRelativeString(source)
        val destFile = File(target, relativePath)

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

    // Execute [moveSingleFile] for each file in the source directory tree.
    return walkWithErrorHandler(source.walkTopDown(), onError, ::moveFile)
}

/**
 * Copies a file in the filesystem from [source] to [target].
 *
 * @return `true` if the file was copied successfully and `false` otherwise.
 */
private fun copyRecursively(
        source: File, target: File,
        overwrite: Boolean, onError: ErrorHandler
): Boolean {
    return source.copyRecursively(target, overwrite, onError)
}

/**
 * Creates a file in the filesystem at [path] with the given [contents].
 *
 * @return `true` if the file was created successfully and `false` otherwise.
 */
private fun createFileFromStream(
        path: File, contents: InputStream = ByteArrayInputStream(ByteArray(0)),
        onError: ErrorHandler
): Boolean {
    if (path.exists()) {
        val action = onError(path, FileAlreadyExistsException(file = path, reason = "The file already exists."))

        if (action == OnErrorAction.TERMINATE) return false
    }

    contents.use { input ->
        path.outputStream().use { output -> input.copyTo(output) }
    }

    return true
}

/**
 * Creates an empty directory in the filesystem at [path].
 *
 * @return `true` if the directory was created successfully and `false` otherwise.
 */
private fun createEmptyDir(path: File, onError: ErrorHandler): Boolean {
    if (path.exists()) {
        val action = onError(path, FileAlreadyExistsException(file = path, reason = "The file already exists."))

        if (action == OnErrorAction.TERMINATE) return false
    }

    return path.mkdirs()
}

/**
 * Deletes the file or directory at [path] from the filesystem.
 *
 * @return `true` if the file was deleted successfully and `false` otherwise.
 */
private fun deleteRecursively(path: File, onError: ErrorHandler): Boolean {
    fun deleteFile(deletePath: File): Boolean {
        try {
            Files.delete(deletePath.toPath())
        } catch (e: IOException) {
            val action = onError(deletePath, e)
            if (action == OnErrorAction.TERMINATE) return false
        }

        return true
    }

    // Execute [deleteSingleFile] for each file in the directory tree.
    return walkWithErrorHandler(path.walkBottomUp(), onError, ::deleteFile)
}

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
 * A set of changes to apply to the filesystem.
 *
 * This class allows for creating a set of changes that can be applied to multiple directories. The [move], [copy],
 * [createFile], [createDir] and [delete] methods are used to queue up new changes which can be applied to the
 * filesystem all at once with [apply].
 *
 * @param [defaultErrorHandler] The default error handler to use for each method that accepts one.
 */
class PathDelta(var defaultErrorHandler: ErrorHandler = ::skipOnError) {
    /**
     * A change to apply to the filesystem.
     *
     * @property [viewFunc] A function that modifies a [MutableDirPath].
     * @property [filesystemFunc] A function that modifies the filesystem.
     */
    private data class Action(val viewFunc: ViewAction, val filesystemFunc: FilesystemAction)

    /**
     * A list of actions to apply to the filesystem.
     */
    private val actions: Deque<Action> = LinkedList()

    /**
     * Moves the file or directory at [source] to [target].
     *
     * @param [overwrite] If a file already exists at [target], replace it.
     * @param [onError] The function used to handle errors.
     *
     * [onError] can be passed the following exceptions:
     * - [NoSuchFileException]: There was an attempt to move a nonexistent file.
     * - [FileAlreadyExistsException]: The destination file already exists.
     * - [AccessDeniedException]: There was an attempt to open a directory that didn't succeed.
     * - [IOException]: Some other problem occurred while moving.
     */
    fun move(source: FSPath, target: FSPath, overwrite: Boolean = false, onError: ErrorHandler = defaultErrorHandler) {
        val viewAction: ViewAction = {
            if (source.startsWith(it)) it.descendants.remove(source)
            if (target.startsWith(it)) it.descendants.add(target as MutableFSPath)
        }
        val filesystemAction = {
            moveRecursively(source.toFile(), target.toFile(), overwrite, onError)
        }

        actions.addLast(Action(viewAction, filesystemAction))
    }

    /**
     * Copies the file or directory at [source] to [target].
     *
     * @param [overwrite] If a file already exists at [target], replace it.
     * @param [onError] The function used to handle errors.
     *
     * [onError] can be passed the following exceptions:
     * - [NoSuchFileException]: There was an attempt to copy a nonexistent file.
     * - [FileAlreadyExistsException]: The destination file already exists.
     * - [AccessDeniedException]: There was an attempt to open a directory that didn't succeed.
     * - [IOException]: Some other problem occurred while copying.
     */
    fun copy(source: FSPath, target: FSPath, overwrite: Boolean = false, onError: ErrorHandler = defaultErrorHandler) {
        val viewAction: ViewAction = {
            if (target.startsWith(it)) it.descendants.add(target as MutableFSPath)
        }
        val filesystemAction = {
            copyRecursively(source.toFile(), target.toFile(), overwrite, onError)
        }

        actions.addLast(Action(viewAction, filesystemAction))
    }

    /**
     * Creates a new file at [path] containing data from [contents].
     *
     * @param [onError] The function used to handle errors.
     *
     * [onError] can be passed the following exceptions:
     * - [FileAlreadyExistsException]: The file already exists.
     * - [IOException]: Some other problem occurred while creating the file.
     */
    fun createFile(path: FilePath, contents: InputStream, onError: ErrorHandler = defaultErrorHandler) {
        val viewAction: ViewAction = { it.descendants.add(path as MutableFSPath) }
        val filesystemAction = { createFileFromStream(path.toFile(), contents, onError) }

        actions.addLast(Action(viewAction, filesystemAction))
    }

    /**
     * Creates a new empty directory at [path].
     *
     * @param [onError] The function used to handle errors.
     *
     * [onError] can be passed the following exceptions:
     * - [FileAlreadyExistsException]: The file already exists.
     * - [IOException]: Some other problem occurred while creating the directory.
     */
    fun createDir(path: DirPath, onError: ErrorHandler = defaultErrorHandler) {
        val viewAction: ViewAction = { it.descendants.add(path as MutableFSPath) }
        val filesystemAction = { createEmptyDir(path.toFile(), onError) }

        actions.addLast(Action(viewAction, filesystemAction))
    }

    /**
     * Deletes the file or directory at [path].
     *
     * @param [onError] The function used to handle errors.
     *
     * [onError] can be passed the following exceptions:
     * - [NoSuchFileException]: There was an attempt to delete a nonexistent file.
     * - [AccessDeniedException]: There was an attempt to open a directory that didn't succeed.
     * - [IOException]: Some other problem occurred while deleting.
     */
    fun delete(path: FSPath, onError: ErrorHandler = defaultErrorHandler) {
        val viewAction: ViewAction = { it.descendants.remove(path) }
        val filesystemAction = { deleteRecursively(path.toFile(), onError) }

        actions.addLast(Action(viewAction, filesystemAction))
    }

    /**
     * Undoes the given number of [changes] and returns how many were undone.
     *
     * @throws [IllegalArgumentException] This is thrown if the given number of changes is negative.
     */
    fun undo(changes: Int): Int {
        require(changes < 0) { "the given number of changes must be positive" }

        val startSize = actions.size
        repeat(changes) { actions.pollLast() }
        return startSize - actions.size
    }

    /**
     * Returns what [dirPath] will look like with all changes applied assuming there are no errors.
     */
    fun getExpected(dirPath: DirPath): DirPath {
        val outputPath = dirPath.toMutableDirPath()
        for (action in actions) {
            action.viewFunc(outputPath)
        }
        return outputPath
    }

    /**
     * Applies the changes in this delta to the filesystem in the order they were made.
     *
     * Any relative paths that were passed in are resolved against [dirPath]. Applying the changes does not consume
     * them. If any [ErrorHandler] throws an exception, it will be thrown here.
     */
    fun apply(dirPath: DirPath) {
        for (action in actions) {
            action.filesystemFunc()
        }
    }
}
