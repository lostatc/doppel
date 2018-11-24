# doppel
doppel is a library for manipulating the filesystem in memory.

## Features
* Represent and manipulate file trees in memory
  * Create file trees using a DSL
  * Get file trees from the filesystem
  * Get the children of a path and efficiently find descendants
  * Associate file types with paths
  * Compare file trees
* Queue up changes to the filesystem in memory
  * Queue up changes and then apply them all at once
  * Undo changes before they're applied
  * See a preview of what the filesystem will look like with changes applied
