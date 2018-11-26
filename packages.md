# Module doppel

Doppel is a library for manipulating the filesystem in memory. It works on top of Java NIO to reduce the need for
expensive filesystem access.

# Package io.github.lostatc.doppel.error

Tools for handling errors that occur when accessing the filesystem.

Many functions, methods and classes that access or modify the filesystem accept an [ErrorHandler] to determine how
errors are handled on a file-by-file basis. This package provides the default error handlers [skipOnError],
[terminateOnError] and [throwOnError].

# Package io.github.lostatc.doppel.filesystem

Classes for accessing and modifying the filesystem.

This package provides the [PathDelta] class which allows changes to the filesystem to be queued up in memory and then
applied all at once. Different types of changes that can be applied are represented by implementations of
[FilesystemAction].

# Package io.github.lostatc.doppel.path

In-memory representations of directory trees.

This package allows directory trees to be represented in memory using [PathNode] and [MutablePathNode].
