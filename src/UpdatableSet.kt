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
     * Notifies each of the [observers] that this object has changed.
     *
     * Observers are notified in the order in which they appear in [observers].
     */
    fun <T> notify(property: KProperty<*>, oldValue: T, newValue: T) {
        observers.forEach { it.update(this, property, oldValue, newValue) }
    }
}

/**
 * A generic mutable set which can contain mutable elements and update itself when they change.
 *
 * @constructor Creates an instance from a collection of elements.
 */
internal class UpdatableSet<E : SimpleObservable>(elements: Collection<E>) : SimpleObserver, MutableSet<E> {

    private val innerSet: MutableSet<E> = hashSetOf()

    init {
        addAll(elements)
    }

    override val size: Int
        get() = innerSet.size

    /**
     * Creates an empty instance.
     */
    constructor() : this(emptyList())

    /**
     * Notifies this set that one of its elements has changed.
     */
    override fun <T> update(observable: SimpleObservable, property: KProperty<*>, oldValue: T, newValue: T) {
        // Clear the set and re-add all items to update the hash codes.
        val copy = innerSet.toList()
        innerSet.clear()
        innerSet.addAll(copy)
    }

    /**
     * Makes this an observer of the [element] and adds it to this collection.
     *
     * @return `true` if the element has been added, `false` if the element is already contained in the collection.
     */
    override fun add(element: E): Boolean {
        element.observers.add(this)
        return innerSet.add(element)
    }

    /**
     * Makes this an observer of each element in [elements] and adds them to this collection.
     *
     * @return `true` if any of the specified elements were added to the collection, `false` if the collection was not
     * modified.
     */
    override fun addAll(elements: Collection<E>): Boolean = elements.filter { add(it) }.any()

    /**
     * Removes this as an observer of [element] and removes it from this collection if it is present.
     *
     * @return `true` if the element has been successfully removed, `false` if it was not present in the collection.
     */
    override fun remove(element: E): Boolean {
        element.observers.remove(this)
        return innerSet.remove(element)
    }

    /**
     * Removes this as an observer of each element in [elements] and removes them from this collection if present.
     *
     * @return `true` if any of the specified elements were removed from the collection, `false` if the collection was
     * not modified.
     */
    override fun removeAll(elements: Collection<E>): Boolean = elements.filter { remove(it) }.any()

    /**
     * Retains only the elements in this collection that are contained in the specified collection.
     *
     * Elements which are removed from this collection also have this removed as an observer.
     *
     * @return `true` if any element was removed from the collection, `false` if the collection was not modified.
     */
    override fun retainAll(elements: Collection<E>): Boolean = removeAll(innerSet.filter { it !in elements })

    /**
     * Removes this as an observer for all elements in the collection and removes them form this collection.
     */
    override fun clear() {
        innerSet.forEach { it.observers.remove(this) }
        innerSet.clear()
    }

    override fun iterator(): MutableIterator<E> = MutableCollectionIterator<E>(this, innerSet.iterator())

    override fun contains(element: E): Boolean = innerSet.contains(element)

    override fun containsAll(elements: Collection<E>): Boolean = innerSet.containsAll(elements)

    override fun isEmpty(): Boolean = innerSet.isEmpty()

    override fun toString(): String = innerSet.toString()

    override fun equals(other: Any?): Boolean = innerSet.equals(other)

    override fun hashCode(): Int = innerSet.hashCode()
}
