# doppel
doppel is a library for manipulating the filesystem in memory. It works on top of Java NIO to reduce the need for
expensive filesystem access.

## Features
* Represent and manipulate directory trees in memory
  * Create directory trees using a DSL
  * Get directory trees from the filesystem
  * Efficiently access the descendants of a directory
  * Associate file types with paths
  * Create files in the filesystem with a specific directory structure
  * Compare file contents
  * Compare directory trees
* Queue up changes to the filesystem in memory
  * Queue up changes and then apply them all at once
  * Undo changes before they're applied
  * See a preview of what the filesystem will look like with changes applied
  * Recursively copy, move and delete files
  * Handle I/O errors on a file-by-file basis
