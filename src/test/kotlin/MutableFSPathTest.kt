import diffir.MutableDirPath
import diffir.MutableFilePath
import io.kotlintest.*
import io.kotlintest.data.forall
import io.kotlintest.matchers.collections.*
import io.kotlintest.specs.WordSpec
import io.kotlintest.tables.row
import java.nio.file.Files
import java.nio.file.Paths

class MutableFSPathTest : WordSpec() {
    init {
        "MutableFSPath constructor" should {
            "throw if the given file name has a parent" {
                shouldThrow<IllegalArgumentException> {
                    MutableFilePath(Paths.get("a", "b"), null)
                }
            }

            "make the last segment the file name" {
                MutableFilePath("parent", "fileName").fileName shouldBe Paths.get("fileName")
                MutableFilePath(Paths.get("parent", "fileName")).fileName shouldBe Paths.get("fileName")
            }

            "make the second-last segment the parent" {
                MutableFilePath("parent", "fileName").parent?.fileName shouldBe Paths.get("parent")
                MutableFilePath(Paths.get("parent", "fileName")).parent?.fileName shouldBe Paths.get("parent")
            }

            "not create a parent from a single segment" {
                MutableFilePath(Paths.get("fileName")).parent shouldBe null
                MutableFilePath("fileName").parent shouldBe null
            }

            "make the path a child of its parent" {
                val newPath = MutableFilePath("parent", "fileName")
                newPath.parent!!.children should contain(newPath)
            }
        }

        "MutableFSPath.path" should {
            "work for multiple segments" {
                MutableFilePath("/", "a", "b").path shouldBe Paths.get("/", "a", "b")
            }

            "work for a single segment" {
                MutableFilePath("a").path shouldBe Paths.get("a")
            }

            "work for a root component" {
                MutableFilePath("/").path shouldBe Paths.get("/")
            }
        }

        "MutableFSPath.toString" should {
            "return a string representation" {
                MutableFilePath("/", "a", "b").toString() shouldBe "/a/b"
            }
        }

        "MutableFSPath.equals" should {
            "return whether two paths are equal" {
                forall(
                    row(MutableFilePath("/", "a", "b"), MutableFilePath("/", "a", "b"), true),
                    row(MutableFilePath("/", "a", "b"), MutableFilePath("a", "b"), false),
                    row(MutableFilePath("/", "a", "b"), MutableFilePath("/", "a", "c"), false),
                    row(MutableFilePath("/", "a", "b"), MutableDirPath("/", "a", "b"), false)
                ) { thisPath, otherPath, result ->
                    (thisPath == otherPath) shouldBe result
                }
            }
        }

        "MutableFSPath.hashCode" should {
            "return the same hash code for equal instances" {
                MutableFilePath("/", "a", "b").hashCode() shouldBe MutableFilePath("/", "a", "b").hashCode()
            }
        }

        "MutableFSPath.exists" should {
            "return whether a path exists in the filesystem" {
                val existingFile = Files.createTempFile("", ".tmp")
                val nonexistentFile = Paths.get("/", "nonexistent")

                forall(
                    row(MutableFilePath(existingFile), true, true),
                    row(MutableDirPath(existingFile), true, false),
                    row(MutableDirPath(existingFile), false, true),
                    row(MutableFilePath(nonexistentFile), true, false),
                    row(MutableFilePath(nonexistentFile), false, false)
                ) { path, checkType, result ->
                    path.exists(checkType = checkType) shouldBe result
                }

                Files.delete(existingFile)
            }
        }

        // TODO: Move this to [MutableFilePath] test.
        "MutableFSPath.copy" should {
            "return a copy that's equal to the original" {
                val newPath = MutableFilePath("/", "a", "b")
                newPath shouldBe newPath.copy()
            }

            "return a copy with a new file name" {
                val newFileName = Paths.get("b")
                val newPath = MutableFilePath(Paths.get("a"))
                newPath.copy(fileName = newFileName).fileName shouldBe newFileName
            }

            "return a copy with a new parent" {
                val newParent = MutableDirPath("/", "a")
                val newPath = MutableFilePath(Paths.get("/", "b", "c"))
                newPath.copy(parent = newParent).parent shouldBe newParent
            }
        }

        "MutableFSPath.startsWith" should {
            "return whether this path starts with another" {
                forall(
                    row(Paths.get("/", "a", "b"), Paths.get("/", "a"), true),
                    row(Paths.get("/", "a", "b"), Paths.get("a", "b"), false),
                    row(Paths.get("a", "b"), Paths.get("a", "b"), true),
                    row(Paths.get("a", "b"), Paths.get("c"), false)
                ) { thisPath, otherPath, result ->
                    MutableFilePath(thisPath).startsWith(MutableFilePath(otherPath)) shouldBe result
                }
            }
        }

        "MutableFSPath.endsWith" should {
            "return whether this path ends with another" {
                forall(
                    row(Paths.get("/", "a", "b"), Paths.get("a", "b"), true),
                    row(Paths.get("/", "a", "b"), Paths.get("/", "a"), false),
                    row(Paths.get("a", "b"), Paths.get("a", "b"), true),
                    row(Paths.get("a", "b"), Paths.get("c"), false)
                ) { thisPath, otherPath, result ->
                    MutableFilePath(thisPath).endsWith(MutableFilePath(otherPath)) shouldBe result
                }
            }
        }
    }
}