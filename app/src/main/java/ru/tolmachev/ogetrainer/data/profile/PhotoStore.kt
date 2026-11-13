package ru.tolmachev.ogetrainer.data.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * Фото профилей: копия выбранного изображения кладётся в filesDir/photos,
 * в базе хранится только относительный путь. Пикер отдаёт content:// URI,
 * который живёт до конца процесса, поэтому копия обязательна.
 */
class PhotoStore(private val context: Context) {

    private val dir: File
        get() = File(context.filesDir, "photos").apply { if (!exists()) mkdirs() }

    /**
     * Сохраняет фото, сжимая до [MAX_SIDE] px по длинной стороне. Возвращает относительный путь.
     * Пикер отдаёт content:// URI, который нельзя читать дважды на всех прошивках, поэтому
     * сначала копируем поток во временный файл, затем декодируем уже с диска.
     */
    fun save(uri: Uri): String? = runCatching {
        val temp = File.createTempFile("photo-", ".bin", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
            } ?: return null
            if (temp.length() == 0L) return null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE)
            }
            val bitmap = BitmapFactory.decodeFile(temp.absolutePath, options) ?: return null

            val name = "${UUID.randomUUID()}.jpg"
            val target = File(dir, name)
            target.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
            bitmap.recycle()
            "photos/$name"
        } finally {
            temp.delete()
        }
    }.getOrNull()

    fun delete(relativePath: String?) {
        if (relativePath.isNullOrBlank()) return
        runCatching { File(context.filesDir, relativePath).delete() }
    }

    fun load(relativePath: String?, targetPx: Int = 256): Bitmap? {
        if (relativePath.isNullOrBlank()) return null
        return runCatching {
            val file = File(context.filesDir, relativePath)
            if (!file.exists()) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetPx)
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
        }.getOrNull()
    }

    private fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= maxSide && h / 2 >= maxSide) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    private companion object {
        const val MAX_SIDE = 1024
    }
}
