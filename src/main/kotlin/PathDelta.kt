package diffir

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.util.*
import kotlin.io.FileAlreadyExistsException


/**
 * A value which determines how an error that occurs during a filesystem operation is handled.
 */
enum class OnErrorAction(internal val visitResult: FileVisitResult) {
    /**
     * Skip the file that caused the error.
     */
    SKIP(FileVisitResult.CONTINUE),

    /**
     * Terminate the filesystem operation.
     */
    TERMINATE(FileVisitResult.TERMINATE)
}

/**
 * A function that is called for each error that occurs during a filesystem operation. Functions of this type are passed
 * the file that caused the error and the exception that was thrown. They return a value which determines how that error
 * is handled.
 */
typealias ErrorHandler = (Path, IOException) -> OnErrorAction

/**
 * Handles filesystem errors by always skipping the file that caused the error.
 */
@Suppress("UNUSED_PARAMETER")
fun skipOnError(file: Path, exception: IOException): OnErrorAction = OnErrorAction.SKIP

/**
 * Handles filesystem errors by always terminating the operation when there is an error.
 */
@Suppress("UNUSED_PARAMETER")
fun terminateOnError(file: Path, exception: IOException): OnErrorAction = OnErrorAction.TERMINATE

/**
 * Handles filesystem errors by always throwing the exception.
 */
@Suppress("UNUSED_PARAMETER")
fun throwOnError(file: Path, exception: IOException): Nothing {
    throw exception
}

/**
 * The default error handler used by functions and classes which accept one.
 */
val DEFAULT_ERROR_HANDLER: ErrorHandler = ::skipOnError

/**
 * Adds [path] to [viewDir] if [path] is relative or a descendant of [viewDir].
 *
 * This function can be used to implement [FilesystemAction.applyView].
 */
fun addPathToView(viewDir: MutableDirPath, path: FSPath) {
    val absolutePath = path.withAncestor(viewDir)
    if (absolutePath.startsWith(viewDir)) viewDir.descendants.add(path as MutableFSPath)
}

/**
 * Removes [path] from [viewDir] if [path] is relative or a descendant of [viewDir].
 *
 * This function can be used to implement [FilesystemAction.applyView].
 */
fun removePathFromView(viewDir: MutableDirPath, path: FSPath) {
    val absolutePath = path.withAncestor(viewDir)
    if (absolutePath.startsWith(viewDir)) viewDir.descendants.remove(path)
}

/**
 * A file visitor that handles errors based on an [ErrorHandler].
 *
 * @property [onError] A function that determines how errors are handled.
 */
private open class ErrorHandlingVisitor(val onError: ErrorHandler) : SimpleFileVisitor<Path>() {
    override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = onError(file, exc).visitResult
}

/**
 * Calls [tryFunc] and passes any thrown [IOException] with [file] to [onError].
 *
 * @return How the file walk should be handled.
 */
private fun handleWalkErrors(onError: ErrorHandler, file: Path, tryFunc: () -> Unit): FileVisitResult {
    try {
        tryFunc()
    } catch (e: IOException) {
        return onError(file, e).visitResult
    }

    return FileVisitResult.CONTINUE
}

/**
 * A change to apply to the filesystem.
 */
interface FilesystemAction {
    /**
     * A function that is called for each error that occurs while changes are being applied which determines how to
     * handle them.
     */
    val onError: ErrorHandler

    /**
     * Modifies [viewDir] to provide a view of what the filesystem will look like after [applyView] is called.
     *
     * After this is called, [viewDir] should match what the filesystem would look like if [applyFilesystem] were called
     * assuming there are no errors. Relative paths passed to [FilesystemAction] instances are resolved against [viewDir].
     *
     * The functions [addPathToView] and [removePathFromView] can be useful in implementing this method.
     */
    fun applyView(viewDir: MutableDirPath)

    /**
     * Applies the change to the filesystem.
     *
     * Relative paths passed to [FilesystemAction] instances are resolved against [dirPath].
     *
     * @return `true` if the action completed successfully or `false` if there were errors.
     */
    fun applyFilesystem(dirPath: DirPath)
}

/**
 * An action that recursively moves a file or directory.
 *
 * If [source] and [target] are the same file, then this action has no effect. If the file to be moved is a symbolic
 * link then the link itself, and not its target, is moved. Moving a file will copy its last modified time if supported
 * by both file stores. This move may or may not be atomic. If it is not atomic and an exception is thrown, the state of
 * the filesystem is not defined.
 *
 * @property [source] The file or directory to move.
 * @property [target] The file or directory to move [source] to.
 * @property [overwrite] If a file already exists in [target], replace it.
 * @property [followLinks] Follow symbolic links when walking the directory tree.
 *
 * [onError] can be passed the following exceptions:
 * - [NoSuchFileException]: There was an attempt to move a nonexistent file.
 * - [FileAlreadyExistsException]: The destination file already exists and [overwrite] is `false`.
 * - [AccessDeniedException]: There was an attempt to open a directory that didn't succeed.
 * - [FileSystemLoopException]: [followLinks] is `true` and a cycle of symbolic links was detected.
 * - [IOException]: Some other problem occurred while moving.
 */
data class MoveAction(
    val source: FSPath, val target: FSPath,
    val overwrite: Boolean = false,
    val followLinks: Boolean = false,
    override val onError: ErrorHandler = DEFAULT_ERROR_HANDLER
) : FilesystemAction {
    override fun applyView(viewDir: MutableDirPath) {
        removePathFromView(viewDir, source)
        addPathToView(viewDir, target)
    }

    override fun applyFilesystem(dirPath: DirPath) {
        val absoluteSource = source.withAncestor(dirPath).toPath()
        val absoluteTarget = target.withAncestor(dirPath).toPath()

        val copyOptions = mutableSetOf<CopyOption>()
        if (overwrite) copyOptions.add(StandardCopyOption.REPLACE_EXISTING)

        val fileVisitor = object : ErrorHandlingVisitor(onError) {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val destFile = absoluteTarget.resolve(absoluteSource.relativize(file))

                return handleWalkErrors(onError, destFile) {
                    Files.move(file, destFile, *copyOptions.toTypedArray())
                }
            }

            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val destDir = absoluteTarget.resolve(absoluteSource.relativize(dir))

                // The directory must be created before you can put files in it.
                return handleWalkErrors(onError, destDir) {
                    Files.copy(dir, destDir, *copyOptions.toTypedArray())
                }
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                val destDir = absoluteTarget.resolve(absoluteSource.relativize(dir))

                // File attributes must be copied to the directory after it has been visited. The directory must be
                // removed from [source] after all its contents have been moved.
                return handleWalkErrors(onError, destDir) {
                    copyFileAttributes(dir, destDir)
                    Files.delete(dir)
                    super.postVisitDirectory(dir, exc)
                }
            }
        }

        val walkOptions = mutableSetOf<FileVisitOption>()
        if (followLinks) walkOptions.add(FileVisitOption.FOLLOW_LINKS)

        Files.walkFileTree(absoluteSource, walkOptions, Int.MAX_VALUE, fileVisitor)
    }
}

/**
 * An action that recursively copies a file or directory.
 *
 * @property [source] The file or directory to copy.
 * @property [target] The file or directory to copy [source] to.
 * @property [overwrite] If a file already exists at [target], replace it.
 * @property [copyAttributes] Attempt to copy file attributes from [source] to [target]. The last modified time is
 * always copied if supported. Whether other attributes are copied is platform and filesystem dependent.
 * @property [followLinks] Follow symbolic links when walking the directory tree and copy the targets of links instead
 * of links themselves. File attributes of links may not be copied when [copyAttributes] is `true`.
 *
 * [onError] can be passed the following exceptions:
 * - [NoSuchFileException]: There was an attempt to copy a nonexistent file.
 * - [FileAlreadyExistsException]: The destination file already exists.
 * - [AccessDeniedException]: There was an attempt to open a directory that didn't succeed.
 * - [FileSystemLoopException]: [followLinks] is `true` and a cycle of symbolic links was detected.
 * - [IOException]: Some other problem occurred while copying.
 */
data class CopyAction(
    val source: FSPath, val target: FSPath,
    val overwrite: Boolean = false,
    val copyAttributes: Boolean = false,
    val followLinks: Boolean = false,
    override val onError: ErrorHandler = DEFAULT_ERROR_HANDLER
) : FilesystemAction {
    override fun applyView(viewDir: MutableDirPath) {
        addPathToView(viewDir, target)
    }

    override fun applyFilesystem(dirPath: DirPath) {
        val absoluteSource = source.withAncestor(dirPath).toPath()
        val absoluteTarget = target.withAncestor(dirPath).toPath()

        val copyOptions = mutableSetOf<CopyOption>()
        if (overwrite) copyOptions.add(StandardCopyOption.REPLACE_EXISTING)
        if (copyAttributes) copyOptions.add(StandardCopyOption.COPY_ATTRIBUTES)
        if (!followLinks) copyOptions.add(LinkOption.NOFOLLOW_LINKS)

        val fileVisitor = object : ErrorHandlingVisitor(onError) {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val destFile = absoluteTarget.resolve(absoluteSource.relativize(file))

                return handleWalkErrors(onError, destFile) {
                    Files.copy(file, destFile, *copyOptions.toTypedArray())
                }
            }

            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val destDir = absoluteTarget.resolve(absoluteSource.relativize(dir))

                // The directory must be created before you can put files in it.
                return handleWalkErrors(onError, destDir) {
                    Files.copy(dir, destDir, *copyOptions.toTypedArray())
                }
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                val destDir = absoluteTarget.resolve(absoluteSource.relativize(dir))

                // File attributes must be copied to the directory after it has been visited.
                return handleWalkErrors(onError, destDir) {
                    if (copyAttributes) copyFileAttributes(dir, destDir)
                    super.postVisitDirectory(dir, exc)
                }
            }
        }

        val walkOptions = mutableSetOf<FileVisitOption>()
        if (followLinks) walkOptions.add(FileVisitOption.FOLLOW_LINKS)

        Files.walkFileTree(absoluteSource, walkOptions, Int.MAX_VALUE, fileVisitor)
    }
}

/**
 * An action that creates a new file with given [attributes] and [contents].
 *
 * @property [path] The path of the new file.
 * @property [attributes] A set of file attributes to set atomically when creating the file.
 * @property [contents] A stream containing the data to fill the file with.
 *
 * [onError] can be passed the following exceptions:
 * - [UnsupportedOperationException]: [attributes] contains an attribute that cannot be set atomically when creating
 *   the file.
 * - [FileAlreadyExistsException]: The file at [path] already exists.
 * - [IOException]: Some other problem occurred while creating the file.
 */
data class CreateFileAction(
    val path: FilePath,
    val attributes: Set<FileAttribute<*>> = emptySet(),
    val contents: InputStream = ByteArrayInputStream(ByteArray(0)),
    override val onError: ErrorHandler = DEFAULT_ERROR_HANDLER
) : FilesystemAction {
    override fun applyView(viewDir: MutableDirPath) {
        addPathToView(viewDir, path)
    }

    override fun applyFilesystem(dirPath: DirPath) {
        val absolutePath = path.withAncestor(dirPath).toPath()

        handleWalkErrors(onError, absolutePath) {
            Files.createFile(absolutePath, *attributes.toTypedArray())
            Files.copy(contents, absolutePath, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

/**
 * An action that creates a new directory and any necessary parent directories with given [attributes].
 *
 * @property [path] The path of the new directory.
 * @property [attributes] A set of file attributes to set atomically when creating the directory.
 *
 * [onError] can be passed the following exceptions:
 * - [UnsupportedOperationException]: [attributes] contains an attribute that cannot be set atomically when creating
 *   the directory.
 * - [FileAlreadyExistsException]: The file already exists but is not a directory.
 * - [IOException]: Some other problem occurred while creating the directory.
 */
data class CreateDirAction(
    val path: DirPath,
    val attributes: Set<FileAttribute<*>> = emptySet(),
    override val onError: ErrorHandler = DEFAULT_ERROR_HANDLER
) : FilesystemAction {
    override fun applyView(viewDir: MutableDirPath) {
        addPathToView(viewDir, path)
    }

    override fun applyFilesystem(dirPath: DirPath) {
        val absolutePath = path.withAncestor(dirPath).toPath()

        handleWalkErrors(onError, absolutePath) {
            Files.createDirectories(absolutePath, *attributes.toTypedArray())
        }
    }
}

/**
 * An action that recursively deletes a file or directory.
 *
 * @property [path] The path of the file or directory to delete.
 * @property [followLinks] Follow symbolic links when walking the directory tree.
 *
 * [onError] can be passed the following exceptions:
 * - [NoSuchFileException]: There was an attempt to delete a nonexistent file.
 * - [AccessDeniedException]: There was an attempt to open a directory that didn't succeed.
 * - [FileSystemLoopException]: [followLinks] is `true` and a cycle of symbolic links was detected.
 * - [IOException]: Some other problem occurred while deleting.
 */
data class DeleteAction(
    val path: FSPath,
    val followLinks: Boolean = false,
    override val onError: ErrorHandler = DEFAULT_ERROR_HANDLER
) : FilesystemAction {
    override fun applyView(viewDir: MutableDirPath) {
        removePathFromView(viewDir, path)
    }

    override fun applyFilesystem(dirPath: DirPath) {
        val absolutePath = path.withAncestor(dirPath).toPath()

        val fileVisitor = object : ErrorHandlingVisitor(onError) {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult =
                handleWalkErrors(onError, file) { Files.delete(file) }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult =
                // The directory cannot be deleted until all its contents have been deleted.
                handleWalkErrors(onError, dir) {
                    Files.delete(dir)
                    super.postVisitDirectory(dir, exc)
                }
        }

        val options = mutableSetOf<FileVisitOption>()
        if (followLinks) options.add(FileVisitOption.FOLLOW_LINKS)

        Files.walkFileTree(absolutePath, options, Int.MAX_VALUE, fileVisitor)
    }
}

/**
 * A set of changes to apply to the filesystem.
 *
 * This class allows for creating a set of changes that can be applied to multiple directories. Changes are stored in a
 * queue and can be applied to the filesystem all at once. New changes can be queued up by passing instances of [FilesystemAction]
 * to the [add] method, but the filesystem is not modified until [apply] is called. There are actions for moving,
 * copying, creating and deleting files and directories. Custom actions can be created by subclassing [FilesystemAction].
 *
 * [FilesystemAction] classes accept both absolute paths and relative paths. If the paths are relative, they are resolved against
 * the path provided to [apply]. Using relative paths allows you to apply the changes to multiple directories.
 *
 * The [view] method can be used to see what a directory will look like after all changes are applied. The [clear] and
 * [undo] methods can be used to undo changes before they're applied.
 */
class PathDelta {
    /**
     * A list of actions to apply to the filesystem.
     */
    private val actions: Deque<FilesystemAction> = LinkedList()

    /**
     * Adds the given [changes] to the queue.
     *
     * These changes are not applied to the filesystem until the [apply] method is called.
     */
    fun add(vararg changes: FilesystemAction) {
        for (change in changes) {
            actions.addLast(change)
        }
    }

    /**
     * Removes the last [numChanges] changes from the queue and returns how many were removed.
     *
     * @throws [IllegalArgumentException] The given [numChanges] is negative.
     */
    fun undo(numChanges: Int): Int {
        require(numChanges > 0) { "the given number of changes must be positive" }

        val startSize = actions.size
        repeat(numChanges) { actions.pollLast() }
        return startSize - actions.size
    }

    /**
     * Removes all changes from the queue and returns how many were removed.
     */
    fun clear(): Int {
        val numActions = actions.size
        actions.clear()
        return numActions
    }

    /**
     * Returns what [dirPath] will look like after the [apply] method is called assuming there are no errors.
     *
     * Any relative paths that were passed to [FilesystemAction] classes are resolved against [dirPath]. This does not modify the
     * filesystem.
     */
    fun view(dirPath: DirPath): DirPath {
        val outputPath = dirPath.toMutableDirPath()
        for (action in actions) {
            action.applyView(outputPath)
        }
        return outputPath
    }

    /**
     * Applies the changes in the queue to the filesystem in the order they were made.
     *
     * Any relative paths that were passed to [FilesystemAction] classes are resolved against [dirPath]. Applying the changes does
     * not consume them. If an [ErrorHandler] that was passed to an [FilesystemAction] class throws an exception, it will be
     * thrown here.
     */
    fun apply(dirPath: DirPath) {
        for (action in actions) {
            action.applyFilesystem(dirPath)
        }
    }
}
