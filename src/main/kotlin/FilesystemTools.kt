package diffir

import java.io.File
import java.io.IOException
import java.security.DigestInputStream
import java.security.MessageDigest

/**
 * Returns a list of paths representing the immediate children of [directory] in the filesystem.
 */
internal fun scanChildren(directory: DirPath): List<MutableFSPath> {
    val dirChildren = directory.toFile().listFiles()
    dirChildren ?: throw IOException(
        "cannot access children because the path is not an accessible directory or because of an IO error"
    )
    return dirChildren.map {
        when {
            it.isDirectory -> MutableDirPath(it.toPath().fileName)
            else -> MutableFilePath(it.toPath().fileName)
        }
    }
}

/**
 * This is the size of the buffer used when computing the checksum of a file.
 */
const val CHECKSUM_BUFFER_SIZE: Int = 4096

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
 * This function computes and returns a SHA-256 checksum of the given [file].
 */
internal fun getFileChecksum(file: File, algorithm: DigestAlgorithm = DigestAlgorithm.SHA256): ByteArray {
    val messageDigest = MessageDigest.getInstance(algorithm.algorithmName)
    val inputStream = file.inputStream()
    val buffer = ByteArray(CHECKSUM_BUFFER_SIZE)

    DigestInputStream(inputStream, messageDigest).use {
        do {
            val bytesRead = it.read(buffer)
        } while (bytesRead != -1)
    }

    return messageDigest.digest()
}
