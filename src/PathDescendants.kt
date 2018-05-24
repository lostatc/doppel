package diffir

import java.util.LinkedList

/**
 * A wrapper for modifying the descendants of a directory path like a set.
 *
 * This class allows for modifying a tree of [MutableFSPath] objects like a set. Items added to the set are inserted
 * into their proper location in the tree, and items removed from the set are removed from the tree.
 *
 * @param [innerPath] The directory path to modify.
 */
internal class PathDescendants(private val innerPath: DirPath) : MutableSet<MutableFSPath> {
    /**
     * A read only set containing all the descendants of the directory.
     */
    private val descendants: Set<MutableFSPath>
        get() = innerPath.walkChildren().toSet()

    /**
     * A read-only list containing all the directories in the tree.
     */
    private val allDirectories: List<DirPath>
        get() = descendants.filterIsInstance<DirPath>() + innerPath

    override val size: Int
        get() = descendants.size

    override fun add(element: MutableFSPath): Boolean {
        val new = element.relativeTo(innerPath)
        var successful = false

        // Get a list of all the ancestors in the hierarchy going back to the root.
        val ancestors = LinkedList<MutableFSPath>()
        var current: MutableFSPath? = new
        while (current != null) {
            ancestors.addFirst(innerPath + current)
            current = current.parent
        }

        // Start at the root of the hierarchy and work downwards to find where the new path should be inserted. At each
        // level, check if that ancestor is in the set of descendants. Make the new path a child of the last ancestor
        // that exists in the set of descendants.
        var parent: MutableFSPath = innerPath
        for (ancestor in ancestors) {
            if (ancestor !in allDirectories) {
                // The ancestor is not in the list of descendants. Add the new path to the parent of this ancestor.
                allDirectories.find { it == parent }?.children?.add(ancestor)
                successful = true
            }
            parent = ancestor
        }

        return successful
    }

    override fun addAll(elements: Collection<MutableFSPath>): Boolean = elements.filter { add(it) }.any()

    override fun clear() {
        innerPath.children.clear()
    }

    override fun iterator(): MutableIterator<MutableFSPath> =
        MutableCollectionIterator<MutableFSPath>(this, descendants.iterator())

    override fun remove(element: MutableFSPath): Boolean {
        val parent = descendants.find { it == element }?.parent ?: return false
        return parent.children.remove(element)
    }

    override fun removeAll(elements: Collection<MutableFSPath>): Boolean = elements.filter { remove(it) }.any()

    override fun retainAll(elements: Collection<MutableFSPath>): Boolean =
        removeAll(descendants.filter { it !in elements })

    override fun contains(element: MutableFSPath): Boolean = descendants.contains(element)

    override fun containsAll(elements: Collection<MutableFSPath>): Boolean = descendants.containsAll(elements)

    override fun isEmpty(): Boolean = descendants.isEmpty()

    override fun toString(): String = descendants.toString()

    override fun equals(other: Any?): Boolean = descendants.equals(other)

    override fun hashCode(): Int = descendants.hashCode()
}