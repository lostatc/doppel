package diffir

/**
 * A wrapper for modifying the children of a directory path.
 *
 * This class modifies the [MutableFSPath.parent] property of its elements to ensure that if B is a child of A, then A
 * is the parent of B.
 */
internal class PathChildren(private val innerPath: MutableDirPath) : MutableSet<MutableFSPath> by innerPath._children {
    private val innerSet: MutableSet<MutableFSPath> = innerPath._children

    /**
     * Makes [innerPath] the parent of [element] and adds it to this collection.
     *
     * @return `true` if the element has been added, `false` if the element is already contained in the collection.
     */
    override fun add(element: MutableFSPath): Boolean {
        element._parent = innerPath
        return innerSet.add(element)
    }

    /**
     * Makes [innerPath] the parent of each element in [elements] and adds them to this collection.
     *
     * @return `true` if any of the specified elements were added to the collection, `false` if the collection was not
     * modified.
     */
    override fun addAll(elements: Collection<MutableFSPath>): Boolean = elements.filter { add(it) }.any()

    /**
     * Sets the parent of [element] to `null` and removes it from this collection if it is present.
     *
     * @return `true` if the element has been successfully removed, `false` if it was not present in the collection.
     */
    override fun remove(element: MutableFSPath): Boolean {
        // The element must be removed from the set before setting its parent to `null`, otherwise it won't be found in
        // the set and won't be removed.
        val elementInSet = innerSet.find { it == element }
        val successful = innerSet.remove(element)
        elementInSet?._parent = null
        return successful
    }

    /**
     * Sets the parent of each element in [elements] to `null` and removes them from this collection if present.
     *
     * @return `true` if any of the specified elements were removed from the collection, `false` if the collection was
     * not modified.
     */
    override fun removeAll(elements: Collection<MutableFSPath>): Boolean = elements.filter { remove(it) }.any()

    /**
     * Retains only the elements in this collection that are contained in the specified collection.
     *
     * Elements which are removed from this collection have their parent set to `null`.
     *
     * @return `true` if any element was removed from the collection, `false` if the collection was not modified.
     */
    override fun retainAll(elements: Collection<MutableFSPath>): Boolean =
        removeAll(innerSet.filter { it !in elements })

    /**
     * Sets the parent of each element in the collection to `null` and removes them from the collection.
     */
    override fun clear() {
        // Creating a copy of the set is required to avoid a ConcurrentModificationException that would otherwise be
        // caused by the setter of [MutableFSPath._parent] notifying the underlying [UpdatableSet] to update.
        innerSet.toList().forEach { it._parent = null }
        innerSet.clear()
    }

    override fun iterator(): MutableIterator<MutableFSPath> =
        MutableCollectionIterator<MutableFSPath>(this, innerSet.iterator())

    override fun toString(): String = innerSet.toString()

    override fun equals(other: Any?): Boolean = innerSet.equals(other)

    override fun hashCode(): Int = innerSet.hashCode()
}
