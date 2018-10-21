package diffir

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.attribute.FileAttribute

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
 * An action that recursively moves a file or directory from [source] to [target].
 *
 * This action is implemented using [moveRecursively]. See its documentation for more information.
 *
 * @property [source] The file or directory to move.
 * @property [target] The file or directory to move [source] to.
 * @property [overwrite] If a file or directory already exists at [target], replace it. If the directory is not empty,
 * it is deleted recursively.
 * @property [followLinks] Follow symbolic links when walking the directory tree.
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

        moveRecursively(
            absoluteSource, absoluteTarget,
            overwrite = overwrite, followLinks = followLinks, onError = onError
        )
    }
}

/**
 * An action that recursively copies a file or directory from [source] to [target].
 *
 * This action is implemented using [copyRecursively]. See its documentation for more information.
 *
 * @property [source] The file or directory to copy.
 * @property [target] The file or directory to copy [source] to.
 * @property [overwrite] If a file or directory already exists at [target], replace it. If the directory is not empty,
 * it is deleted recursively.
 * @property [copyAttributes] Attempt to copy file attributes from [source] to [target]. The last modified time is
 * always copied if supported. Whether other attributes are copied is platform and filesystem dependent. File attributes
 * of links may not be copied.
 * @property [followLinks] Follow symbolic links when walking the directory tree and copy the targets of links instead
 * of links themselves.
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

        copyRecursively(
            absoluteSource, absoluteTarget,
            overwrite = overwrite, copyAttributes = copyAttributes, followLinks = followLinks, onError = onError
        )
    }
}

/**
 * An action that creates a new file at [path] with given [attributes] and [contents].
 *
 * This action is implemented using [createFile]. See its documentation for more information.
 *
 * @property [path] The path of the new file.
 * @property [attributes] A set of file attributes to set atomically when creating the file.
 * @property [contents] A stream containing the data to fill the file with.
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

        createFile(absolutePath, attributes = attributes, contents = contents, onError = onError)
    }
}

/**
 * An action that creates a symbolic link named [link] pointing to [target].
 *
 * This action is implemented using [createSymbolicLink]. See its documentation for more information.
 *
 * @property [link] The path of the symbolic link.
 * @property [target] The path the symbolic link points to.
 * @property [attributes] A set of file attributes to set atomically when creating the file.
 */
data class CreateSymbolicLinkAction(
    val link: FilePath,
    val target: DirPath,
    val attributes: Set<FileAttribute<*>> = emptySet(),
    override val onError: ErrorHandler = DEFAULT_ERROR_HANDLER
) : FilesystemAction {
    override fun applyView(viewDir: MutableDirPath) {
        addPathToView(viewDir, link)
    }

    override fun applyFilesystem(dirPath: DirPath) {
        val absoluteLinkPath = link.withAncestor(dirPath).toPath()
        val absoluteTargetPath = target.withAncestor(dirPath).toPath()

        createSymbolicLink(absoluteLinkPath, absoluteTargetPath, attributes = attributes, onError = onError)
    }
}

/**
 * An action that creates a new directory and [path] with any necessary parent directories and given [attributes].
 *
 * This action is implemented using [createDir]. See its documentation for more information.
 *
 * @property [path] The path of the new directory.
 * @property [attributes] A set of file attributes to set atomically when creating the directory.
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

        createDir(absolutePath, attributes = attributes, onError = onError)
    }
}

/**
 * An action that recursively deletes a file or directory at [path].
 *
 * This action is implemented using [deleteRecursively]. See its documentation for more information.
 *
 * @property [path] The path of the file or directory to delete.
 * @property [followLinks] Follow symbolic links when walking the directory tree.
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

        deleteRecursively(absolutePath, followLinks = followLinks, onError = onError)
    }
}