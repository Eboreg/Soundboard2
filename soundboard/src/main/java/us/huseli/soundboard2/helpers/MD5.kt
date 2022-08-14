package us.huseli.soundboard2.helpers

import java.io.*
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/** Heavily "inspired" by https://stackoverflow.com/a/14922433/14311882 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
object MD5 {
    /**
     * @throws NoSuchAlgorithmException
     * @throws IOException
     */
    fun calculate(inputStream: InputStream): String {
        val buf = ByteArray(8192)
        var len: Int
        val digest = MessageDigest.getInstance("MD5")
        while (inputStream.read(buf).also { len = it } > 0) {
            digest.update(buf, 0, len)
        }
        val bigInt = BigInteger(1, digest.digest())
        return String.format("%32s", bigInt.toString(16)).replace(" ", "0")
    }

    /**
     * @throws FileNotFoundException
     * @throws NoSuchAlgorithmException
     * @throws IOException
     */
    fun calculate(file: File): String = FileInputStream(file).use { calculate(it) }

    fun calculate(fd: FileDescriptor): String = FileInputStream(fd).use { calculate(it) }

    /** Maybe I'll not use this, but it's good to have anyway */
    fun check(md5: String, file: File) = calculate(file).equals(md5, ignoreCase = true)
}