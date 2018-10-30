package diffir

import java.util.*

/**
 * A wrapper for modifying the descendants of a directory path like a set.
 *
 * This class allows for modifying a tree of [MutableFSPath] objects like a set. Items added to the set are inserted
 * into their proper location in the tree, and items removed from the set are removed from the tree.
 *
 * @param [innerPath] The directory path that this object represents the descendants of.
 */
internal class PathDescendants(private val innerPath: MutableDirPath) : MutableSet<MutableFSPath> {
    /**
     * A read-only set containing all the descendants of the directory.
     */
    private val descendants: Set<MutableFSPath>
        get() = innerPath.walkChildren().toSet()

    override val size: Int
        get() = descendants.size

    /**
     * Inserts [element] into its proper location in the tree.
     *
     * If [element] is a relative path, then it is considered to be relative to [innerPath].
     *
     * @throws [IllegalArgumentException] This exception is thrown if [element] is absolute and not a descendant of
     * [innerPath].
     *
     * @return `true` if the element has been added, `false` if the element is already contained in the collection.
     */
    override fun add(element: MutableFSPath): Boolean {
        require(!element.path.isAbsolute || element.startsWith(innerPath)) {
            "The given path must either be relative or be a descendant of this path."
        }

        val newPath = innerPath.resolve(element)

        // Get the longest common path between the new path and the descendants of this directory.
        var longestCommonPath = innerPath
        for (dirPath in innerPath.walkChildren().filterIsInstance<MutableDirPath>()) {
            if (newPath.startsWith(dirPath)) longestCommonPath = dirPath
        }

        // Check if the path already exists in the tree.
        if (newPath in longestCommonPath.children) return false

        // Add the new path as a child of the longest common path.
        val relativeNewPath = longestCommonPath.relativize(newPath)
        longestCommonPath.children.add(relativeNewPath.root)

        return true
    }

    /**
     * Inserts each element in [elements] into its proper location in the tree.
     *
     * @return `true` if any of the specified elements were added to the collection, `false` if the collection was not
     * modified.
     */
    override fun addAll(elements: Collection<MutableFSPath>): Boolean = elements.filter { add(it) }.any()

    /**
     * Removes [element] from its location in the tree.
     *
     * @return `true` if the element has been successfully removed, `false` if it was not present in the collection.
     */
    override fun remove(element: MutableFSPath): Boolean {
        val parent = descendants.find { it == element }?.parent ?: return false
        return parent.children.remove(element)
    }

    /**
     * Removes each element in [elements] from its location in the tree.
     *
     * @return `true` if any of the specified elements were removed from the collection, `false` if the collection was
     * not modified.
     */
    override fun removeAll(elements: Collection<MutableFSPath>): Boolean = elements.filter { remove(it) }.any()

    /**
     * Retains only the elements in the tree that are contained in the specified collection.
     *
     * @return `true` if any element was removed from the collection, `false` if the collection was not modified.
     */
    override fun retainAll(elements: Collection<MutableFSPath>): Boolean =
        removeAll(descendants.filter { it !in elements })

    /**
     * Removes all the paths in the tree.
     */
    override fun clear() {
        innerPath.children.clear()
    }

    override fun iterator(): MutableIterator<MutableFSPath> =
        MutableCollectionIterator(this, descendants.iterator())

    override fun contains(element: MutableFSPath): Boolean = descendants.contains(element)

    override fun containsAll(elements: Collection<MutableFSPath>): Boolean = descendants.containsAll(elements)

    override fun isEmpty(): Boolean = descendants.isEmpty()

    override fun toString(): String = descendants.toString()

    override fun equals(other: Any?): Boolean = descendants == other

    override fun hashCode(): Int = descendants.hashCode()
}