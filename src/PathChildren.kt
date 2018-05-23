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
        element._parent = innerPath
        return innerSet.add(element)
    }

    override fun addAll(elements: Collection<MutableFSPath>): Boolean = elements.filter { add(it) }.any()

    override fun remove(element: MutableFSPath): Boolean {
        innerSet.find { it == element }?._parent = null
        return innerSet.remove(element)
    }

    override fun removeAll(elements: Collection<MutableFSPath>): Boolean {
        innerSet.filter { it in elements }.forEach { it._parent = null }
        return innerSet.removeAll(elements)
    }

    override fun retainAll(elements: Collection<MutableFSPath>): Boolean =
        removeAll(innerSet.filter { it !in elements })

    override fun clear() {
        // Creating a copy of the set is required to avoid a ConcurrentModificationException that would otherwise be
        // caused by the setter of [MutableFSPath::parent] notifying the underlying [UpdatableSet] to update.
        innerSet.toList().forEach { it._parent = null }
        innerSet.clear()
    }

    override fun iterator(): MutableIterator<MutableFSPath> = object : MutableIterator<MutableFSPath> {
        /**
         * The last item returned by this iterator. This property is used to implement [remove].
         */
        private lateinit var previousItem: MutableFSPath

        private val innerIterator = innerSet.iterator()

        override fun hasNext(): Boolean = innerIterator.hasNext()

        override fun next(): MutableFSPath {
            previousItem = innerIterator.next()
            return previousItem
        }

        override fun remove() {
            remove(previousItem)
        }
    }

    override fun toString(): String = innerSet.toString()

    override fun equals(other: Any?): Boolean = innerSet.equals(other)

    override fun hashCode(): Int = innerSet.hashCode()
}
