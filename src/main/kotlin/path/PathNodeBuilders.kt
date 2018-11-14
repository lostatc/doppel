package diffir.path

import java.nio.file.Path

/**
 * Builds a path node from the given file segments and file type.
 *
 * This can be used to create builder methods for use with [MutablePathNode.of] to create a tree of path nodes.
 *
 * @param [firstSegment] The first segment of the new path node.
 * @param [segments] The remaining segments of the new path node.
 * @param [type] The initial type of the created node.
 */
fun MutablePathNode.pathNode(
    firstSegment: String, vararg segments: String,
    type: FileType
): MutablePathNode {
    val newPath = path.fileSystem.getPath(firstSegment, *segments)
    val pathNode = MutablePathNode.of(newPath, type)
    addDescendant(pathNode)
    return pathNode
}

/**
 * Builds a path node from the given file segments with a type of [RegularFileType].
 *
 * This is meant to be used with [MutablePathNode.of] to construct a tree of path nodes.
 *
 * @param [firstSegment] The first segment of the new path node.
 * @param [segments] The remaining segments of the new path node.
 */
fun MutablePathNode.file(firstSegment: String, vararg segments: String): MutablePathNode =
    pathNode(firstSegment, *segments, type = RegularFileType())

/**
 * Builds a path node from the given file segments with a type of [DirectoryType].
 *
 * This is meant to be used with [MutablePathNode.of] to construct a tree of path nodes.
 *
 * @param [firstSegment] The first segment of the new path node.
 * @param [segments] The remaining segments of the new path node.
 * @param [init] A function literal with receiver in which you can construct children.
 */
fun MutablePathNode.dir(
    firstSegment: String, vararg segments: String,
    init: MutablePathNode.() -> Unit = {}
): MutablePathNode {
    val pathNode = pathNode(firstSegment, *segments, type = DirectoryType())
    pathNode.init()
    return pathNode
}

/**
 * Builds a path node from the given file segments with a type of [SymbolicLinkType].
 *
 * This is meant to be used with [MutablePathNode.of] to construct a tree of path nodes.
 *
 * @param [firstSegment] The first segment of the new path node.
 * @param [segments] The remaining segments of the new path node.
 * @param [target] The path the link points to.
 */
fun MutablePathNode.symlink(firstSegment: String, vararg segments: String, target: Path): MutablePathNode =
    pathNode(firstSegment, *segments, type = SymbolicLinkType(target))

/**
 * Builds a path node from the given file segments with a type of [UnknownType].
 *
 * This is meant to be used with [MutablePathNode.of] to construct a tree of path nodes.
 *
 * @param [firstSegment] The first segment of the new path node.
 * @param [segments] The remaining segments of the new path node.
 */
fun MutablePathNode.unknown(firstSegment: String, vararg segments: String): MutablePathNode =
    pathNode(firstSegment, *segments, type = UnknownType())
