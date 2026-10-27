package com.mobilegamma.cakesync.orders

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.mobilegamma.cakesync.edit.PhotoEditor
import java.io.File

/**
 * The customer's reference photos, copied into the app so they stay with the order: the picked
 * URI is only valid until the phone restarts, a file in `files/order-photos` is not.
 */
object OrderPhotos {
    private const val DIR = "order-photos"

    private fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    /**
     * Copies [uri] into the app and returns the path to keep on the order, or null if the image
     * can't be read. [orderId] in the name makes the files easy to find and clean up.
     */
    fun save(context: Context, uri: Uri, orderId: String): String? = runCatching {
        val bitmap = PhotoEditor.loadScaled(context, uri, 1600)
        val file = File(dir(context), "${orderId.replace(Regex("[^A-Za-z0-9_-]"), "_")}-${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        file.absolutePath
    }.getOrNull()

    /** A bitmap small enough to draw ([max] pixels on the long side); null if the file is gone. */
    fun load(path: String, max: Int = 480): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= max) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    /** True for a picture this app saved (web form links are plain URLs and can't be drawn). */
    fun isLocal(path: String) = path.isNotBlank() && !path.startsWith("http", true)

    fun delete(path: String) {
        if (isLocal(path)) runCatching { File(path).delete() }
    }
}
