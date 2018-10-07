package diffir

/**
 * A set that delegates all members to the set returned by [provider].
 */
internal class ViewSet<E>(private val provider: () -> Set<E>) : Set<E> {
    override val size: Int = provider().size

    override fun contains(element: E): Boolean = provider().contains(element)

    override fun containsAll(elements: Collection<E>): Boolean = provider().containsAll(elements)

    override fun isEmpty(): Boolean = provider().isEmpty()

    override fun iterator(): Iterator<E> = provider().iterator()

    override fun toString(): String = provider().toString()

    override fun equals(other: Any?): Boolean = provider() == other

    override fun hashCode(): Int = provider().hashCode()
}
