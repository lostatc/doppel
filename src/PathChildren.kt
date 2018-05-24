package diffir

/**
 * A wrapper for modifying the children of a directory path.
 *
 * Whenever children are added to this set, the [MutableFSPath::parent] property of each of them is set to [innerPath].
 * Whenever children are removed from this set, the [MutableFSPath::parent] property of each of them is set to `null`.
 */
internal class PathChildren(private val innerPath: DirPath) : MutableSet<MutableFSPath> by innerPath._children {
    private val innerSet: MutableSet<MutableFSPath> = innerPath._children

    override fun add(element: MutableFSPath): Boolean {
        // The element must be inserted into the set before setting its parent so that the set is registered as an
        // observer for the element.
        val successful = innerSet.add(element)
        element._parent = innerPath
        return successful
    }

    override fun addAll(elements: Collection<MutableFSPath>): Boolean = elements.filter { add(it) }.any()

    override fun remove(element: MutableFSPath): Boolean {
        // The element must be removed from the set before setting its parent to `null`, otherwise it won't be found in
        // the set and won't be removed.
        val elementInSet = innerSet.find { it == element }
        val successful = innerSet.remove(element)
        elementInSet?._parent = null
        return successful
    }

    override fun removeAll(elements: Collection<MutableFSPath>): Boolean = elements.filter { remove(it) }.any()

    override fun retainAll(elements: Collection<MutableFSPath>): Boolean =
        removeAll(innerSet.filter { it !in elements })

    override fun clear() {
        // Creating a copy of the set is required to avoid a ConcurrentModificationException that would otherwise be
        // caused by the setter of [MutableFSPath::parent] notifying the underlying [UpdatableSet] to update.
        innerSet.toList().forEach { it._parent = null }
        innerSet.clear()
    }

    override fun iterator(): MutableIterator<MutableFSPath> =
        MutableCollectionIterator<MutableFSPath>(this, innerSet.iterator())

    override fun toString(): String = innerSet.toString()

    override fun equals(other: Any?): Boolean = innerSet.equals(other)

    override fun hashCode(): Int = innerSet.hashCode()
}
