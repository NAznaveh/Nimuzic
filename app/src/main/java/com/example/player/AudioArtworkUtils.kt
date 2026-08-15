package com.example.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import java.io.File
import java.io.FileInputStream
import kotlin.math.max

object AudioArtworkUtils {

    /**
     * Fast non-invasive header check to determine if an audio file contains embedded artwork,
     * preventing MediaMetadataRetrieverJNI from emitting "getEmbeddedPicture failed" error logs.
     */
    fun hasEmbeddedArtwork(filePath: String): Boolean {
        if (filePath.isBlank() || filePath.startsWith("http://") || filePath.startsWith("https://") ||
            filePath.startsWith("android.resource://") || filePath.startsWith("asset:")
        ) {
            return false
        }

        val file = File(filePath)
        if (!file.exists() || !file.canRead() || file.length() < 128) {
            return false
        }

        return try {
            FileInputStream(file).use { input ->
                val header = ByteArray(12)
                val readHeader = input.read(header)
                if (readHeader < 10) return false

                // 1. MP3 with ID3v2 tag
                if (header[0] == 0x49.toByte() && header[1] == 0x44.toByte() && header[2] == 0x33.toByte()) {
                    val tagSize = ((header[6].toInt() and 0x7F) shl 21) or
                            ((header[7].toInt() and 0x7F) shl 14) or
                            ((header[8].toInt() and 0x7F) shl 7) or
                            (header[9].toInt() and 0x7F)
                    if (tagSize <= 0) return false

                    val scanLimit = minOf(tagSize, 256 * 1024)
                    val buffer = ByteArray(scanLimit)
                    val bytesRead = input.read(buffer)
                    if (bytesRead > 4) {
                        return containsSequence(buffer, bytesRead, "APIC".toByteArray(Charsets.ISO_8859_1)) ||
                                containsSequence(buffer, bytesRead, "PIC".toByteArray(Charsets.ISO_8859_1))
                    }
                    return false
                }

                // 2. M4A / MP4 / AAC container
                if (header[4] == 0x66.toByte() && header[5] == 0x74.toByte() && header[6] == 0x79.toByte() && header[7] == 0x70.toByte()) { // "ftyp"
                    val scanLimit = minOf(file.length().toInt(), 256 * 1024)
                    val buffer = ByteArray(scanLimit)
                    System.arraycopy(header, 0, buffer, 0, header.size)
                    val remainingRead = input.read(buffer, header.size, scanLimit - header.size)
                    val totalRead = header.size + (if (remainingRead > 0) remainingRead else 0)
                    return containsSequence(buffer, totalRead, "covr".toByteArray(Charsets.ISO_8859_1))
                }

                // 3. FLAC
                if (header[0] == 0x66.toByte() && header[1] == 0x4C.toByte() && header[2] == 0x61.toByte() && header[3] == 0x43.toByte()) { // "fLaC"
                    // Parse FLAC metadata blocks to check for PICTURE block (type 6)
                    var isLast = false
                    while (!isLast) {
                        val blockHeader = ByteArray(4)
                        if (input.read(blockHeader) < 4) break
                        isLast = (blockHeader[0].toInt() and 0x80) != 0
                        val blockType = blockHeader[0].toInt() and 0x7F
                        val length = ((blockHeader[1].toInt() and 0xFF) shl 16) or
                                ((blockHeader[2].toInt() and 0xFF) shl 8) or
                                (blockHeader[3].toInt() and 0xFF)
                        if (blockType == 6) {
                            return true
                        }
                        if (length > 0) {
                            input.skip(length.toLong())
                        }
                    }
                    return false
                }

                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun containsSequence(src: ByteArray, length: Int, target: ByteArray): Boolean {
        if (target.isEmpty() || length < target.size) return false
        val maxIndex = length - target.size
        for (i in 0..maxIndex) {
            var found = true
            for (j in target.indices) {
                if (src[i + j] != target[j]) {
                    found = false
                    break
                }
            }
            if (found) return true
        }
        return false
    }

    /**
     * Safely extracts embedded picture bytes only if artwork is confirmed to be present.
     */
    fun extractEmbeddedArtworkBytes(filePath: String): ByteArray? {
        if (!hasEmbeddedArtwork(filePath)) {
            return null
        }
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            val bytes = retriever.embeddedPicture
            try { retriever.release() } catch (_: Exception) {}
            if (bytes != null && bytes.isNotEmpty()) bytes else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Decodes and downsamples a bitmap safely to prevent ashmem memory warnings or pinning issues.
     */
    fun decodeSampledBitmap(bytes: ByteArray, maxDimension: Int = 192): Bitmap? {
        if (bytes.isEmpty()) return null
        return try {
            val boundsOpts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
            val origWidth = boundsOpts.outWidth
            val origHeight = boundsOpts.outHeight
            if (origWidth <= 0 || origHeight <= 0) return null

            val maxDim = max(origWidth, origHeight)
            var sampleSize = 1
            while ((maxDim / sampleSize) > maxDimension) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null
            if (decoded.width > maxDimension || decoded.height > maxDimension) {
                val scale = maxDimension.toFloat() / max(decoded.width, decoded.height)
                val targetW = (decoded.width * scale).toInt().coerceAtLeast(1)
                val targetH = (decoded.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
            } else {
                decoded
            }
        } catch (_: Exception) {
            null
        }
    }
}
