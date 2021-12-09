package org.factcast.schema.registry.cli.utils

import org.apache.commons.io.FileUtils
import java.io.*
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.createDirectories
import kotlin.io.path.exists


/**
 * UnzipUtils class extracts files and sub-directories of a standard zip file to
 * a destination directory.
 *
 * Taken from https://gist.github.com/NitinPraksash9911/dea21ec4b8ae7df068f8f891187b6d1e
 * and modified to work with InputStreams and java.nio.Path and removed bug that files in
 * subdirectories made it crash.
 *
 */
object UnzipUtils {

    /**
     * @param zipFileInputStream input stream of a zip file. will be closed after reading.
     * @param destDirectory
     * @throws IOException
     */
    @Throws(IOException::class)
    fun unzip(zipFileInputStream: InputStream, destDirectory: Path) {
        if (!destDirectory.exists()) {
            destDirectory.createDirectories()
        }

        val tmpZipFile = File.createTempFile("unzip-schema-reg", ".zip");

        try {
            FileUtils.copyInputStreamToFile(zipFileInputStream, tmpZipFile)

            ZipFile(tmpZipFile).use { zip ->

                zip.entries().asSequence().forEach { entry ->

                    zip.getInputStream(entry).use { input ->

                        val filePath = destDirectory.resolve(entry.name)

                        if (!entry.isDirectory) {
                            // if the entry is a file, extracts it
                            extractFile(input, filePath)
                        } else {
                            // if the entry is an empty directory or was explicitly added, create the directory
                            filePath.createDirectories()
                        }

                    }

                }
            }
        } finally {
            FileUtils.deleteQuietly(tmpZipFile)
        }
    }

    /**
     * Extracts a zip entry (file entry)
     * @param inputStream
     * @param destFilePath
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun extractFile(inputStream: InputStream, destFilePath: Path) {

        // sometimes required
        destFilePath.parent.createDirectories()

        val bos = BufferedOutputStream(FileOutputStream(destFilePath.toFile()))
        val bytesIn = ByteArray(BUFFER_SIZE)
        var read: Int
        while (inputStream.read(bytesIn).also { read = it } != -1) {
            bos.write(bytesIn, 0, read)
        }
        bos.close()
    }

    /**
     * Size of the buffer to read/write data
     */
    private const val BUFFER_SIZE = 4096

}