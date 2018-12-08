/*
 * Copyright © 2018 Garrett Powell <garrett@gpowell.net>
 *
 * This file is part of doppel.
 *
 * doppel is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * doppel is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with doppel.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.lostatc.doppel.handlers

import java.nio.file.FileVisitResult
import java.nio.file.Path

/**
 * A value which determines how an error that occurs during a file system operation is handled.
 *
 * @property [visitResult] The corresponding [FileVisitResult] to return when walking a file tree.
 */
enum class ErrorHandlerAction(val visitResult: FileVisitResult) {
    /**
     * Skip the file that caused the error.
     */
    SKIP(FileVisitResult.CONTINUE),

    /**
     * Terminate the file system operation.
     */
    TERMINATE(FileVisitResult.TERMINATE)
}

/**
 * A class which determines how errors that occur during a file system operation are handled.
 */
interface ErrorHandler {
    /**
     * This function is called for each error that occurs.
     *
     * @param [path] The path of the file that caused the error. If the error involves both a source file and a target
     * file, then the source file is passed in.
     * @param [exception] An exception that describes the error.
     *
     * @return A value which determines how the error is handled.
     */
    fun handle(path: Path, exception: Exception): ErrorHandlerAction
}

/**
 * An error handler that handles file system errors by always skipping the file that caused the error.
 */
class SkipHandler : ErrorHandler {
    override fun handle(path: Path, exception: Exception): ErrorHandlerAction = ErrorHandlerAction.SKIP
}

/**
 * An error handler that handles file system errors by always terminating the operation.
 */
class TerminateHandler : ErrorHandler {
    override fun handle(path: Path, exception: Exception): ErrorHandlerAction = ErrorHandlerAction.TERMINATE
}

/**
 * An error handler that handles file system errors by always throwing the exception.
 */
class ThrowHandler : ErrorHandler {
    override fun handle(path: Path, exception: Exception): Nothing = throw exception
}
