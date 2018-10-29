import diffir.DirPath
import diffir.FilePath
import diffir.MutableDirPath
import diffir.WalkDirection
import io.kotlintest.*
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
class DirTreeListener : TestListener {
    /**
     * A directory path representing the tree of files and directories.
     */
    lateinit var path: DirPath

    override fun beforeTest(description: Description) {
        val tempPath = Files.createTempDirectory("")
        path = MutableDirPath.of(tempPath) {
            file("b")
            dir("c", "d") {
                file("e")
                dir("f")
            }
        }

        for (descendant in path.descendants) {
            when (descendant) {
                is DirPath -> Files.createDirectories(descendant.path)
                is FilePath -> Files.createFile(descendant.path)
            }
        }
    }

    override fun afterTest(description: Description, result: TestResult) {
        for (descendant in path.walkChildren(WalkDirection.BOTTOM_UP)) {
            Files.delete(descendant.path)
        }
    }
}