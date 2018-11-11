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
import io.kotlintest.properties.Gen.Companion.file
import io.kotlintest.specs.WordSpec
import io.kotlintest.tables.row
import java.io.IOException
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
    val dirTreeListener: DirTreeListener = DirTreeListener()

    override fun listeners(): List<TestListener> = listOf(nonexistentListener, dirTreeListener)

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

            "return a relativized path when one is the parent of the other" {
                val newPath = MutableDirPath("a", "b")
                val otherPath = MutableDirPath("a", "b", "c")

                newPath.relativize(otherPath).shouldBe(MutableDirPath("c"))
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

        "MutableDirPath.findDescendant" should {
            "return the descendant from a relative path" {
                val directory = MutableDirPath.of(Paths.get("a")) {
                    dir("b") {
                        dir("c") {
                            file("d")
                        }
                    }
                    dir("e")
                }

                val descendant = directory.findDescendant(MutableDirPath("a", "b", "c"))

                assertSoftly {
                    descendant.shouldBe(MutableDirPath("a", "b", "c"))
                    descendant?.children?.shouldContainExactly(MutableFilePath("a", "b", "c", "d"))
                }
            }

            "throw if the given path does not start with this path" {
                shouldThrow<IllegalArgumentException> {
                    val directory = MutableDirPath("a", "b")
                    directory.findDescendant(MutableDirPath("c", "d"))
                }
            }
        }

        "MutableDirPath.treeExists" should {
            "identify that an existing tree exists" {
                dirTreeListener.dirPath.treeExists(checkType = false).shouldBeTrue()
            }

            "identify that the file types in an existing tree match" {
                dirTreeListener.dirPath.treeExists(checkType = true).shouldBeTrue()
            }

            "identify that a nonexistent tree doesn't exist" {
                val randomPath = dirTreeListener.dirPath.descendants
                    .filterIsInstance<MutableFilePath>()
                    .random()
                    .path

                Files.delete(randomPath)

                dirTreeListener.dirPath.treeExists(checkType = false).shouldBeFalse()
            }

            "identify if file types in an existing tree don't match" {
                val randomPath = dirTreeListener.dirPath.descendants
                    .filterIsInstance<MutableFilePath>()
                    .random()
                    .path

                Files.delete(randomPath)
                Files.createDirectory(randomPath)

                assertSoftly {
                    dirTreeListener.dirPath.treeExists(checkType = true).shouldBeFalse()
                    dirTreeListener.dirPath.treeExists(checkType = false).shouldBeTrue()
                }
            }
        }

        "MutableDirPath.diff" should {
            // TODO: Add tests

            "provide properties for accessing paths" {
                val leftPath = MutableDirPath("/", "a")
                val rightPath = MutableDirPath("/", "b")
                val diff = leftPath.diff(rightPath)

                diff.left.shouldBe(leftPath)
                diff.right.shouldBe(rightPath)
            }

            "correctly compare paths in the directories" {
                val leftPath = MutableDirPath.of(Paths.get("/")) {
                    dir("a") {
                        file("b")
                        dir("c")
                    }
                    file("d")
                }

                val rightPath = MutableDirPath.of(Paths.get("/")) {
                    dir("a") {
                        file("b")
                    }
                    file("e")
                }

                val diff = leftPath.diff(rightPath)
                val expectedCommon = setOf(MutableDirPath("a"), MutableFilePath("a", "b"))
                val expectedLeftOnly = setOf(MutableDirPath("a", "c"), MutableFilePath("d"))
                val expectedRightOnly = setOf(MutableFilePath("e"))

                assertSoftly {
                    diff.common.shouldBe(expectedCommon)
                    diff.leftOnly.shouldBe(expectedLeftOnly)
                    diff.rightOnly.shouldBe(expectedRightOnly)
                }
            }

            "correctly compare file contents" {
                val leftPath = MutableDirPath(Files.createTempDirectory(""))
                val rightPath = MutableDirPath(Files.createTempDirectory(""))

                val leftSame = leftPath.resolve(MutableFilePath("same"))
                val rightSame = rightPath.resolve(MutableFilePath("same"))
                Files.write(leftSame.path, listOf("same contents"))
                Files.write(rightSame.path, listOf("same contents"))

                val leftDifferent = leftPath.resolve(MutableFilePath("different"))
                val rightDifferent = rightPath.resolve(MutableFilePath("different"))
                Files.write(leftDifferent.path, listOf("different contents 1"))
                Files.write(rightDifferent.path, listOf("different contents 2"))

                val diff = leftPath.diff(rightPath)

                assertSoftly {
                    diff.same.shouldContainExactly(leftPath.relativize(leftSame))
                    diff.different.shouldContainExactly(leftPath.relativize(leftDifferent))
                }

                cleanUpDirectory(leftPath)
                cleanUpDirectory(rightPath)
            }

            "correctly compare directory contents" {
                val leftPath = MutableDirPath.of(Files.createTempDirectory("")) {
                    dir("same") {
                        file("file")
                    }
                    dir("different") {
                        file("file1")
                    }
                }

                val rightPath = MutableDirPath.of(Files.createTempDirectory("")) {
                    dir("same") {
                        file("file")
                    }
                    dir("different") {
                        file("file2")
                    }
                }

                val diff = leftPath.diff(rightPath)

                assertSoftly {
                    diff.same.shouldContainExactly(MutableDirPath("same"))
                    diff.different.shouldContainExactly(MutableDirPath("different"))
                }
            }

            "correctly compare file times" {

            }

            "skip on I/O errors and continue" {

            }

            "terminate on I/O errors" {

            }
        }

        "MutableDirPath.scanChildren" should {
            "get the children from the filesystem" {
                val testDir = MutableDirPath(dirTreeListener.dirPath.path)
                testDir.scanChildren()

                testDir.children.shouldBe(dirTreeListener.dirPath.children)
            }

            "only get the immediate children" {
                val testDir = MutableDirPath(dirTreeListener.dirPath.path)
                testDir.scanChildren()

                testDir.descendants.shouldBe(dirTreeListener.dirPath.children)
            }

            "throw if the directory doesn't exist" {
                val testDir = MutableDirPath(nonexistentListener.nonexistentFile)
                shouldThrow<IOException> {
                    testDir.scanChildren()
                }
            }
        }

        "MutableDirPath.scanDescendants" should {
            "get the descendants from the filesystem" {
                val testDir = MutableDirPath(dirTreeListener.dirPath.path)
                testDir.scanDescendants()

                testDir.descendants.shouldBe(dirTreeListener.dirPath.descendants)
            }

            "throw if the directory doesn't exist" {
                val testDir = MutableDirPath(nonexistentListener.nonexistentFile)
                shouldThrow<IOException> {
                    testDir.scanDescendants()
                }
            }
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