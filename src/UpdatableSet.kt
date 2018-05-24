package diffir

import kotlin.reflect.KProperty

/**
 * A class that is notified of changes in observable objects.
 */
interface SimpleObserver {
    /**
     * This method is called whenever an observed method is changed.
     */
    fun <T> update(observable: SimpleObservable, property: KProperty<*>, oldValue: T, newValue: T)
}

/**
 * A class that can be observed for changes.
 *
 * Objects of this type can have multiple observers which are notified of any changes to the object.
 */
interface SimpleObservable {
    /**
     * The observers to be notified when this object changes.
     */
    val observers: MutableList<SimpleObserver>

    /**
     * Notify each of the [observers] that this object has changed.
     *
     * Observers are notified in the order in which they appear in [observers].
     */
    fun <T> notify(property: KProperty<*>, oldValue: T, newValue: T) {
        observers.forEach { it.update(this, property, oldValue, newValue) }
    }
}

/**
 * A set which can contain mutable elements and update itself when they change.
 *
 * @constructor Create an instance from a collection of elements.
 */
internal class UpdatableSet<E : SimpleObservable>(elements: Collection<E>) : SimpleObserver, MutableSet<E> {

    private val innerSet: MutableSet<E> = hashSetOf()

    init {
        addAll(elements)
    }

    override val size: Int
        get() = innerSet.size

    /**
     * Create an empty instance.
     */
    constructor() : this(emptyList())

    override fun <T> update(observable: SimpleObservable, property: KProperty<*>, oldValue: T, newValue: T) {
        // Clear the set and re-add all items to update the hash codes.
        val copy = innerSet.toList()
        innerSet.clear()
        innerSet.addAll(copy)
    }

    override fun add(element: E): Boolean {
        element.observers.add(this)
        return innerSet.add(element)
    }

    override fun addAll(elements: Collection<E>): Boolean = elements.filter { add(it) }.any()

    override fun remove(element: E): Boolean {
        element.observers.remove(this)
        return innerSet.remove(element)
    }

    override fun removeAll(elements: Collection<E>): Boolean = elements.filter { remove(it) }.any()

    override fun retainAll(elements: Collection<E>): Boolean = removeAll(innerSet.filter { it !in elements })

    override fun clear() {
        innerSet.forEach { it.observers.remove(this) }
        innerSet.clear()
    }

    override fun iterator(): MutableIterator<E> = object : MutableIterator<E> {
        /**
         * The last item returned by this iterator. This property is used to implement [remove].
         */
        private lateinit var previousItem: E

        private val innerIterator = innerSet.iterator()

        override fun hasNext(): Boolean = innerIterator.hasNext()

        override fun next(): E {
            previousItem = innerIterator.next()
            return previousItem
        }

        override fun remove() {
            remove(previousItem)
        }
    }

    override fun contains(element: E): Boolean = innerSet.contains(element)

    override fun containsAll(elements: Collection<E>): Boolean = innerSet.containsAll(elements)

    override fun isEmpty(): Boolean = innerSet.isEmpty()

    override fun toString(): String = innerSet.toString()

    override fun equals(other: Any?): Boolean = innerSet.equals(other)

    override fun hashCode(): Int = innerSet.hashCode()
}
