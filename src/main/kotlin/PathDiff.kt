package diffir

import java.nio.file.Path

/**
 * An immutable comparison of two path nodes.
 *
 * @property [left] The first path node to compare.
 * @property [right] The second path node to compare.
 * @property [common] The relative paths of files that exist in both directory trees.
 * @property [leftOnly] The relative paths of files that exist in the left tree but not the right tree.
 * @property [rightOnly] The relative paths of files that exist in the right tree but not the left tree.
 * @property [same] The relative paths of files that are the same in both directory trees. Files are the same if they
 * have the same relative path and the same contents.
 * @property [different] The relative paths of files that are different in both directory trees. Files are different if
 * they have the same relative path and different contents.
 * @property [leftNewer] The paths of files that were modified more recently in the left tree than in the right tree.
 * @property [rightNewer] The paths of files that were modified more recently in the right tree than in the left tree.
 *
 */
data class PathDiff(
    val left: PathNode, val right: PathNode,
    val common: Set<Path>,
    val leftOnly: Set<Path>, val rightOnly: Set<Path>,
    val same: Set<Path>, val different: Set<Path>,
    val leftNewer: Set<Path>, val rightNewer: Set<Path>
)