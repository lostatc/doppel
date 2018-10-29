import diffir.MutableDirPath
import diffir.MutableFilePath
import io.kotlintest.*
import io.kotlintest.inspectors.forAll
import io.kotlintest.matchers.collections.shouldContainExactly
import io.kotlintest.specs.WordSpec

class PathChildrenTest : WordSpec() {

    init {
        "PathChildren.add" should {
            "set the parent of paths that don't have one" {
                val newPath = MutableDirPath("/", "a")
                val newChild = MutableFilePath("b")
                newPath.children.add(newChild)

                newPath.children.shouldContainExactly(MutableFilePath("/", "a", "b"))
            }

            "replace the parent of paths that have one" {
                val newPath = MutableDirPath("/", "a")
                val newChild = MutableFilePath("/", "b")
                newPath.children.add(newChild)

                newPath.children.shouldContainExactly(MutableFilePath("/", "a", "b"))
            }
        }

        "PathChildren.addAll" should {
            "set the parents of paths that don't have one" {
                val newPath = MutableDirPath("/", "a")
                val newChildren = listOf(MutableFilePath("b"), MutableFilePath("c"))
                newPath.children.addAll(newChildren)

                newPath.children.forAll {
                    it.parent.shouldBe(newPath)
                }

            }

            "replace the parents of paths that have one" {
                val newPath = MutableDirPath("/", "a")
                val newChildren = listOf(MutableFilePath("/", "b"), MutableFilePath("/", "c"))
                newPath.children.addAll(newChildren)

                newPath.children.forAll {
                    it.parent.shouldBe(newPath)
                }

            }
        }
    }
}
