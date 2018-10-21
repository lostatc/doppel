package diffir

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Path

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
