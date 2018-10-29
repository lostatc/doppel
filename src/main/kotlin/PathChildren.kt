package diffir

/**
 * A wrapper for modifying the children of a directory path.
 *
 * @param [innerPath] The directory path that this object represents the children of.
 * @param [innerSet] The backing set that contains the children of the directory.
 */
internal class PathChildren(
    private val innerPath: MutableDirPath,
    private val innerSet: MutableSet<MutableFSPath>
) : MutableSet<MutableFSPath> by innerSet {
    /**
     * Create a copy of [element] with [innerPath] as its parent and add it to the collection.
     *
     * @return `true` if the element has been added, `false` if the element is already contained in the collection.
     *
     * @throws [IllegalArgumentException] The given path is associated with a different filesystem.
     */
    override fun add(element: MutableFSPath): Boolean {
        require(element.path.fileSystem == innerPath.path.fileSystem) {
            "Cannot add a path associated with a different filesystem."
        }

        val newElement = element.copy(parent = innerPath)
        return innerSet.add(newElement)
    }

    /**
     * Create a copy of each element in [elements] with [innerPath] as the parent and add them to the collection.
     *
     * @return `true` if any of the specified elements were added to the collection, `false` if the collection was not
     * modified.
     */
    override fun addAll(elements: Collection<MutableFSPath>): Boolean = elements.filter { add(it) }.any()

    // These methods must be delegated explicitly because they are not a part of the [MutableSet] interface.

    override fun toString(): String = innerSet.toString()

    override fun equals(other: Any?): Boolean = innerSet == other

    override fun hashCode(): Int = innerSet.hashCode()
}
