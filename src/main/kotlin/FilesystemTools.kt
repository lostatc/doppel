package diffir

import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlin.streams.toList

/**
 * Returns a list of paths representing the immediate children of [directory] in the filesystem.
 */
internal fun scanChildren(directory: DirPath): List<MutableFSPath> {
    return Files.list(directory.toPath()).map {
        when {
            Files.isDirectory(it) -> MutableDirPath(it.fileName)
            else -> MutableFilePath(it.fileName)
        }
    }.toList()
}

/**
 * Copies basic file attributes from [source] to [target].
 */
internal fun copyFileAttributes(source: Path, target: Path) {
    val mtime = Files.getLastModifiedTime(source)
    Files.setLastModifiedTime(target, mtime)
}

/**
 * This is the size of the buffer used when computing the checksum of a file.
 */
private const val CHECKSUM_BUFFER_SIZE: Int = 4096

/**
 * An algorithm used to create a message digest.
 *
 * @property [algorithmName] The name of the algorithm.
 */
internal enum class DigestAlgorithm(val algorithmName: String) {
    /**
     * The MD5 message digest algorithm as defined in [RFC 1321][http://www.ietf.org/rfc/rfc1319.txt].
     */
    MD5("MD5"),

    /**
     * The SHA-1 hash algorithm defined in the [FIPS PUB 180-2][https://csrc.nist.gov/publications/fips].
     */
    SHA1("SHA-1"),

    /**
     * The SHA-256 hash algorithm defined in the [FIPS PUB 180-2][https://csrc.nist.gov/publications/fips].
     */
    SHA256("SHA-256")
}

/**
 * This function computes and returns a checksum of the given [file] using the given [algorithm].
 */
internal fun getFileChecksum(file: Path, algorithm: DigestAlgorithm = DigestAlgorithm.SHA256): ByteArray {
    val messageDigest = MessageDigest.getInstance(algorithm.algorithmName)
    val inputStream = Files.newInputStream(file)
    val buffer = ByteArray(CHECKSUM_BUFFER_SIZE)

    DigestInputStream(inputStream, messageDigest).use {
        do {
            val bytesRead = it.read(buffer)
        } while (bytesRead != -1)
    }

    return messageDigest.digest()
}
