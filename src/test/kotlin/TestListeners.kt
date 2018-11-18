import doppel.path.MutablePathNode
import doppel.path.PathNode
import doppel.path.WalkDirection
import doppel.path.dir
import doppel.path.file
import io.kotlintest.Description
import io.kotlintest.Spec
import io.kotlintest.TestResult
import io.kotlintest.extensions.TestListener
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * A test listener that creates existing and a nonexistent files for testing.
 */
class NonexistentFileListener : TestListener {
    /**
     * A file that exists in the filesystem.
     */
    lateinit var existingFile: Path

    /**
     * A directory that exists in the filesystem.
     */
    lateinit var existingDir: Path

    /**
     * A file that does not exist in the filesystem.
     */
    lateinit var nonexistentFile: Path

    override fun beforeSpec(description: Description, spec: Spec) {
        existingFile = Files.createTempFile("", ".tmp")
        existingDir = Files.createTempDirectory("")
        nonexistentFile = Paths.get("/", "nonexistent")
    }

    override fun afterSpec(description: Description, spec: Spec) {
        Files.deleteIfExists(existingFile)
        Files.deleteIfExists(existingDir)
    }
}

/**
 * A test listener that creates a tree of files and directories in the filesystem.
 */
class FileTreeListener : TestListener {
    /**
     * A directory path representing the tree of files and directories.
     */
    lateinit var pathNode: PathNode

    override fun beforeTest(description: Description) {
        val tempPath = Files.createTempDirectory("")
        pathNode = MutablePathNode.of(tempPath) {
            file("b")
            dir("c", "d") {
                file("e")
                dir("f")
            }
        }

        pathNode.createFile(recursive = true)
    }

    override fun afterTest(description: Description, result: TestResult) {
        for (descendant in pathNode.walkChildren(WalkDirection.BOTTOM_UP)) {
            Files.deleteIfExists(descendant.path)
        }
        Files.deleteIfExists(pathNode.path)
    }
}