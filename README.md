# doppel
Doppel is a library for manipulating the file system in memory. It works on top of Java NIO to reduce the need for
expensive file system access.

[API Documentation](https://lostatc.github.io/doppel/api/doppel)

## Features
* Represent and manipulate directory trees in memory
  * Create directory trees using a DSL
  * Get directory trees from the file system
  * Efficiently access the descendants of a directory
  * Associate file types with paths
  * Create files in the file system with a specific directory structure
  * Compare file contents
  * Compare directory trees
* Queue up changes to the file system in memory
  * Queue up changes and then apply them all at once
  * Undo changes before they're applied
  * See a preview of what the file system will look like with changes applied
  * Recursively move, copy, create and delete files
  * Handle I/O errors on a file-by-file basis
