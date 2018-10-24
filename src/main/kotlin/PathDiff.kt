package diffir

/**
 * An immutable comparison of two directories.
 *
 * @property [left] The first directory to compare.
 * @property [right] The second directory to compare.
 * @property [common] The relative paths of files and directories that exist in both directory trees.
 * @property [leftOnly] The relative paths of files and directories that exist in the left tree but not the right tree.
 * @property [rightOnly] The relative paths of files and directories that exist in the right tree but not the left tree.
 * @property [same] The relative paths of files and directories that are the same in both directory trees. Files are the
 * same if they have the same relative path and the same contents. Directories are the same if they have the same
 * relative path and the same [children][DirPath.children].
 * @property [different] The relative paths of files and directories that are different in both directory trees. Files
 * are different if they have the same relative path and different contents. Directories are the same if they have the
 * same relative path and different [children][DirPath.children].
 * @property [leftNewer] The paths of files and directories that were modified more recently in the left tree than in
 * the right tree.
 * @property [rightNewer] The paths of files and directories that were modified more recently in the right tree than in
 * the left tree.
 */
data class PathDiff(
    val left: DirPath, val right: DirPath,
    val common: Set<FSPath>,
    val leftOnly: Set<FSPath>, val rightOnly: Set<FSPath>,
    val same: Set<FSPath>, val different: Set<FSPath>,
    val leftNewer: Set<FSPath>, val rightNewer: Set<FSPath>
)