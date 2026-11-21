package ru.mesh.expressive.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * Generates a realistic broken/truncated camera JPEG file with valid SOI and EXIF APP1 metadata
 * (Make, Model, Software, DateTime) followed by truncated/corrupted image scan data.
 */
object FakeCameraJpegGenerator {

    fun createBrokenCameraJpegUri(context: Context): Uri {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(context.cacheDir, "IMG_${timestamp}_broken.jpg")
        val bytes = buildBrokenJpegBytes()
        file.writeBytes(bytes)
        android.util.Log.d("MeshWorkPhoto", "Generated fake broken camera JPEG: ${file.absolutePath} (${bytes.size} bytes)")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun buildBrokenJpegBytes(): ByteArray {
        val out = ByteArrayOutputStream()

        // 1. SOI (Start of Image)
        out.write(0xFF)
        out.write(0xD8)

        // 2. APP1 (EXIF)
        val exifPayload = buildExifPayload()
        val app1Length = exifPayload.size + 2
        out.write(0xFF)
        out.write(0xE1)
        out.write((app1Length ushr 8) and 0xFF)
        out.write(app1Length and 0xFF)
        out.write(exifPayload)

        // 3. DQT (Define Quantization Table - standard luminance table)
        out.write(
            byteArrayOf(
                0xFF.toByte(), 0xDB.toByte(), 0x00, 0x43, 0x00,
                16, 11, 10, 16, 24, 40, 51, 61,
                12, 12, 14, 19, 26, 58, 60, 55,
                14, 13, 16, 24, 40, 57, 69, 56,
                14, 17, 22, 29, 51, 87, 80, 62,
                18, 22, 37, 56, 68, 109, 103, 77,
                24, 35, 55, 64, 81, 104, 113, 92,
                49, 64, 78, 87, 103, 121, 120, 101,
                72, 92, 95, 98, 112, 100, 103, 99
            )
        )

        // 4. SOF0 (Baseline DCT) header claiming 4000x3000 3-channel image
        out.write(
            byteArrayOf(
                0xFF.toByte(), 0xC0.toByte(),
                0x00, 0x11, // length = 17
                0x08,       // 8-bit precision
                0x0B, 0xB8.toByte(), // height = 3000
                0x0F, 0xA0.toByte(), // width = 4000
                0x03,       // 3 components
                0x01, 0x22, 0x00,
                0x02, 0x11, 0x01,
                0x03, 0x11, 0x01
            )
        )

        // 5. SOS (Start of Scan) followed by truncated/corrupted entropy-coded data (no EOI FF D9)
        out.write(
            byteArrayOf(
                0xFF.toByte(), 0xDA.toByte(),
                0x00, 0x0C, // length = 12
                0x03,
                0x01, 0x00,
                0x02, 0x11,
                0x03, 0x11,
                0x00, 0x3F, 0x00
            )
        )

        // Realistic ~6 KB truncated camera scan payload (avoiding 0xFF 0xD9 EOI marker)
        val corruptedScan = ByteArray(6144) {
            val b = Random.nextInt(0, 250)
            b.toByte()
        }
        out.write(corruptedScan)

        return out.toByteArray()
    }

    private fun buildExifPayload(): ByteArray {
        val dateStr = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date())
        val tags = listOf(
            0x010F to "Google\u0000",
            0x0110 to "Pixel 9a\u0000",
            0x0131 to "HDR+ 1.0.642815932zd\u0000",
            0x0132 to "$dateStr\u0000",
            0x013B to "Google Camera\u0000"
        )

        val ifdStartOffset = 8 // right after TIFF header
        val ifdSize = 2 + tags.size * 12 + 4 // count(2) + entries(12*N) + nextIfdOffset(4)
        var dataOffset = ifdStartOffset + ifdSize

        val tiffBuf = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
        // TIFF Header: "II", 0x002A, IFD0 offset = 8
        tiffBuf.put('I'.code.toByte())
        tiffBuf.put('I'.code.toByte())
        tiffBuf.putShort(0x002A)
        tiffBuf.putInt(ifdStartOffset)

        // IFD0 entry count
        tiffBuf.putShort(tags.size.toShort())

        val stringData = ByteArrayOutputStream()
        for ((tagId, value) in tags) {
            val bytes = value.toByteArray(Charsets.US_ASCII)
            tiffBuf.putShort(tagId.toShort())
            tiffBuf.putShort(2) // ASCII type
            tiffBuf.putInt(bytes.size)
            if (bytes.size <= 4) {
                val padded = ByteArray(4)
                System.arraycopy(bytes, 0, padded, 0, bytes.size)
                tiffBuf.put(padded)
            } else {
                tiffBuf.putInt(dataOffset)
                stringData.write(bytes)
                dataOffset += bytes.size
            }
        }
        // Next IFD offset = 0
        tiffBuf.putInt(0)
        tiffBuf.put(stringData.toByteArray())

        val tiffBytes = ByteArray(tiffBuf.position())
        tiffBuf.rewind()
        tiffBuf.get(tiffBytes)

        val exifHeader = "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII)
        return exifHeader + tiffBytes
    }
}
