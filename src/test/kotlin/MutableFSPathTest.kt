import diffir.DirPath
import diffir.MutableDirPath
import diffir.MutableFilePath
import diffir.WalkDirection
import io.kotlintest.*
import io.kotlintest.data.forall
import io.kotlintest.extensions.TestListener
import io.kotlintest.matchers.boolean.shouldBeFalse
import io.kotlintest.matchers.boolean.shouldBeTrue
import io.kotlintest.matchers.collections.*
import io.kotlintest.specs.WordSpec
import io.kotlintest.tables.row
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
                MutableFilePath("parent", "fileName").fileName.shouldBe(Paths.get("fileName"))
                MutableFilePath(Paths.get("parent", "fileName")).fileName.shouldBe(Paths.get("fileName"))
            }

            "make the second-last segment the parent" {
                MutableFilePath("parent", "fileName").parent?.fileName.shouldBe(Paths.get("parent"))
                MutableFilePath(Paths.get("parent", "fileName")).parent?.fileName.shouldBe(Paths.get("parent"))
            }

            "not create a parent from a single segment" {
                MutableFilePath(Paths.get("fileName")).parent.shouldBe(null)
                MutableFilePath("fileName").parent.shouldBe(null)
            }

            "make the path a child of its parent" {
                val newPath = MutableFilePath("parent", "fileName")
                newPath.parent!!.children should contain(newPath)
            }
        }

        "MutableFSPath.root" should {
            "return the root node for absolute paths" {
                MutableFilePath("/", "a", "b").root.shouldBe(MutableDirPath("/"))
            }

            "return the root node for relative paths" {
                MutableFilePath("a", "b", "c").root.shouldBe(MutableDirPath("a"))
            }
        }

        "MutableFSPath.path" should {
            "work for multiple segments" {
                MutableFilePath("/", "a", "b").path.shouldBe(Paths.get("/", "a", "b"))
            }

            "work for a single segment" {
                MutableFilePath("a").path.shouldBe(Paths.get("a"))
            }

            "work for a root component" {
                MutableFilePath("/").path.shouldBe(Paths.get("/"))
            }
        }

        "MutableFSPath.toString" should {
            "return a string representation" {
                MutableFilePath("/", "a", "b").toString().shouldBe("/a/b")
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
                    (thisPath == otherPath).shouldBe(result)
                }
            }
        }

        "MutableFSPath.hashCode" should {
            "return the same hash code for equal instances" {
                MutableFilePath("/", "a", "b").hashCode().shouldBe(MutableFilePath("/", "a", "b").hashCode())
            }
        }

        "MutableFSPath.walkAncestors" should {
            "return all ancestors" {
                val testPath = MutableFilePath("/", "a", "b")
                val ancestors = setOf(MutableDirPath("/", "a"), MutableDirPath("/"))
                testPath.walkAncestors().toSet().shouldBe(ancestors)
            }

            "iterate in the correct order" {
                val testPath = MutableFilePath("/", "a", "b")
                val expectedBottomUp = listOf(MutableDirPath("/", "a"), MutableDirPath("/"))
                val expectedTopDown = expectedBottomUp.reversed()

                testPath.walkAncestors(WalkDirection.BOTTOM_UP).toList().shouldBe(expectedBottomUp)
                testPath.walkAncestors(WalkDirection.TOP_DOWN).toList().shouldBe(expectedTopDown)
            }

            "return an empty list when there are no ancestors" {
                MutableFilePath("a").walkAncestors().toList().shouldBe(emptyList())
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
                    MutableFilePath(thisPath).startsWith(MutableFilePath(otherPath)).shouldBe(result)
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
                    MutableFilePath(thisPath).endsWith(MutableFilePath(otherPath)).shouldBe(result)
                }
            }
        }
    }
}

class MutableFilePathTest : WordSpec() {

    val nonexistentListener: NonexistentFileListener = NonexistentFileListener()

    override fun listeners(): List<TestListener> = listOf(nonexistentListener)

    init {
        "MutableFilePath.exists" should {
            "identify that an existing file exists" {
                val newPath = MutableFilePath(nonexistentListener.existingFile)
                newPath.exists(checkType = false).shouldBeTrue()
                newPath.exists(checkType = true).shouldBeTrue()
            }

            "identify that a file is a different type" {
                val newPath = MutableFilePath(nonexistentListener.existingDir)
                newPath.exists(checkType = false).shouldBeTrue()
                newPath.exists(checkType = true).shouldBeFalse()
            }

            "identify that a nonexistent file doesn't exist" {
                val newPath = MutableFilePath(nonexistentListener.nonexistentFile)
                newPath.exists(checkType = false).shouldBeFalse()
                newPath.exists(checkType = true).shouldBeFalse()

            }
        }

        "MutableFilePath.copy" should {
            "return a copy that's equal to the original" {
                val newPath = MutableFilePath("/", "a", "b")
                newPath.shouldBe(newPath.copy())
            }

            "return a copy with a new file name" {
                val newFileName = Paths.get("b")
                val newPath = MutableFilePath(Paths.get("a"))
                newPath.copy(fileName = newFileName).fileName.shouldBe(newFileName)
            }

            "return a copy with a new parent" {
                val newParent = MutableDirPath("/", "a")
                val newPath = MutableFilePath(Paths.get("/", "b", "c"))
                newPath.copy(parent = newParent).parent.shouldBe(newParent)
            }
        }
    }
}

class MutableDirPathTest : WordSpec() {

    val nonexistentListener: NonexistentFileListener = NonexistentFileListener()

    override fun listeners(): List<TestListener> = listOf(nonexistentListener)

    init {
        "MutableDirPath.exists" should {
            "identify that an existing file exists" {
                val newPath = MutableDirPath(nonexistentListener.existingDir)
                newPath.exists(checkType = false).shouldBeTrue()
                newPath.exists(checkType = true).shouldBeTrue()
            }

            "identify that a file is a different type" {
                val newPath = MutableDirPath(nonexistentListener.existingFile)
                newPath.exists(checkType = false).shouldBeTrue()
                newPath.exists(checkType = true).shouldBeFalse()
            }

            "identify that a nonexistent file doesn't exist" {
                val newPath = MutableDirPath(nonexistentListener.nonexistentFile)
                newPath.exists(checkType = false).shouldBeFalse()
                newPath.exists(checkType = true).shouldBeFalse()

            }
        }

        "MutableDirPath.copy" should {
            "return a copy that's equal to the original" {
                val newPath = MutableDirPath("/", "a", "b")
                newPath.shouldBe(newPath.copy())
            }

            "return a copy with a new file name" {
                val newFileName = Paths.get("b")
                val newPath = MutableDirPath(Paths.get("a"))
                newPath.copy(fileName = newFileName).fileName.shouldBe(newFileName)
            }

            "return a copy with a new parent" {
                val newParent = MutableDirPath("/", "a")
                val newPath = MutableDirPath(Paths.get("/", "b", "c"))
                newPath.copy(parent = newParent).parent.shouldBe(newParent)
            }
        }

        "MutableDirPath.relativize" should {
            "throw if the given path does not start with this path" {
                val newPath = MutableDirPath("/", "a", "b")
                shouldThrow<IllegalArgumentException> {
                    newPath.relativize(MutableFilePath("/", "c"))
                }
            }

            "throw if one path is absolute and one is relative" {
                val newPath = MutableDirPath("/", "a", "b")
                shouldThrow<IllegalArgumentException> {
                    newPath.relativize(MutableFilePath("a", "b", "c"))
                }
            }

            "return a relativized path from two absolute paths" {
                val newPath = MutableDirPath("/", "a")
                val otherPath = MutableDirPath("/", "a", "b", "c")

                newPath.relativize(otherPath).shouldBe(MutableDirPath("b", "c"))
            }

            "return a relativized path from two relative paths" {
                val newPath = MutableDirPath("a", "b")
                val otherPath = MutableDirPath("a", "b", "c", "d")

                newPath.relativize(otherPath).shouldBe(MutableDirPath("c",  "d"))
            }

            "work with immutable paths" {
                val newPath = MutableDirPath("a", "b") as DirPath
                val otherPath = MutableDirPath("a", "b", "c", "d") as DirPath

                newPath.relativize(otherPath).shouldBe(MutableDirPath("c", "d") as DirPath)
            }

            "work with file paths" {
                val newPath = MutableDirPath("a", "b")
                val otherPath = MutableFilePath("a", "b", "c", "d")

                newPath.relativize(otherPath).shouldBe(MutableFilePath("c",  "d"))
            }
        }

        "MutableDirPath.resolve" should {
            "return the given path if it is absolute" {
                val newPath = MutableDirPath("/", "a")
                val otherPath = MutableDirPath("/", "b")

                newPath.resolve(otherPath).shouldBe(otherPath)
            }

            "resolve the given path against an absolute path" {
                val newPath = MutableDirPath("/", "a")
                val otherPath = MutableDirPath("b", "c")

                newPath.resolve(otherPath).shouldBe(MutableDirPath("/", "a", "b", "c"))
            }

            "resolve the given path against a relative path" {
                val newPath = MutableDirPath("a")
                val otherPath = MutableDirPath("b", "c")

                newPath.resolve(otherPath).shouldBe(MutableDirPath("a", "b", "c"))
            }

            "work with immutable paths" {
                val newPath = MutableDirPath("a") as DirPath
                val otherPath = MutableDirPath("b", "c") as DirPath

                newPath.resolve(otherPath).shouldBe(MutableDirPath("a", "b", "c") as DirPath)
            }

            "work with file paths" {
                val newPath = MutableDirPath("a")
                val otherPath = MutableFilePath("b", "c")

                newPath.resolve(otherPath).shouldBe(MutableFilePath("a", "b", "c"))
            }
        }

        "MutableDirPath.walkChildren" should {
            "return a sequence containing all descendants" {
                val testPath = MutableDirPath.of(Paths.get("/", "a")) {
                    file("b")
                    dir("c", "d") {
                        file("e")
                        dir("f")
                    }
                }

                testPath.walkChildren().toSet().shouldBe(testPath.descendants)
            }

            "iterate in the correct order" {
                val testPath = MutableDirPath.of(Paths.get("/", "a")) {
                    dir("b") {
                        dir("c") {
                            file("d")
                        }
                    }
                }

                val expectedTopDown = listOf(
                    MutableDirPath("/", "a", "b"),
                    MutableDirPath("/", "a", "b", "c"),
                    MutableFilePath("/", "a", "b", "c", "d")
                )
                val expectedBottomUp = expectedTopDown.reversed()

                testPath.walkChildren(WalkDirection.TOP_DOWN).toList().shouldBe(expectedTopDown)
                testPath.walkChildren(WalkDirection.BOTTOM_UP).toList().shouldBe(expectedBottomUp)
            }

            "return an empty list when there are no children" {
                MutableDirPath("a").walkChildren().toList().shouldBe(emptyList())
            }
        }

        "MutableDirPath.treeExists" should {
            // TODO: Add tests
        }

        "MutableDirPath.diff" should {
            // TODO: Add tests
        }

        "MutableDirPath.findChildren" should {
            // TODO: Add tests
        }

        "MutableDirPath.findDescendants" should {
            // TODO: Add tests
        }

        "MutableDirPath.of" should {
            "create a file hierarchy containing all the given paths" {
                val factoryPath = MutableDirPath.of(Paths.get("/", "a")) {
                    file("b")
                    dir("c", "d") {
                        file("e")
                        dir("f")
                    }
                }

                val testDescendants = setOf(
                    MutableFilePath("/", "a", "b"),
                    MutableDirPath("/", "a", "c"),
                    MutableDirPath("/", "a", "c", "d"),
                    MutableFilePath("/", "a", "c", "d", "e"),
                    MutableDirPath("/", "a", "c", "d", "f")
                )

                factoryPath.descendants.shouldBe(testDescendants)
            }
        }
    }
}