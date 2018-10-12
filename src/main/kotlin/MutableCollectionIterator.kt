package diffir

/**
 * A generic implementation of a mutable iterator to be used for implementing a mutable collection.
 *
 * @param [outerClass] The mutable collection that is using this mutable iterator.
 * @param [iterator] The underlying iterator to use.
 */
class MutableCollectionIterator<E : Any>(
    private val outerClass: MutableCollection<E>,
    private val iterator: Iterator<E>
) : MutableIterator<E> {
    /**
     * The last item returned by this iterator. This property is used to implement [remove].
     */
    private lateinit var previousItem: E

    override fun hasNext(): Boolean = iterator.hasNext()

    override fun next(): E {
        previousItem = iterator.next()
        return previousItem
    }

    override fun remove() {
        outerClass.remove(previousItem)
    }
}