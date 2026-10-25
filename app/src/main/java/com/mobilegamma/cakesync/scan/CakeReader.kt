package com.mobilegamma.cakesync.scan

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.mobilegamma.cakesync.orders.PhotoSuggest

/**
 * Reads a cake photo on the phone with our own models (trained on cake photos, see
 * scripts/cake_model): counts simple, complex and 3D-character toppers and edible prints, and guesses the number of tiers.
 * A model that isn't in assets is skipped, so the app works without them.
 */
class CakeReader(private val context: Context) {

    /** Counts keyed by [PhotoSuggest] labels; empty when no model is installed. */
    fun read(bitmap: Bitmap): Map<String, Int> {
        val assets = context.assets.list("").orEmpty().toSet()
        val image = BitmapImageBuilder(bitmap).build()
        val found = mutableMapOf<String, Int>()
        if (PARTS in assets) {
            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(PARTS).build())
                .setScoreThreshold(PART_SCORE).setMaxResults(40).build()
            ObjectDetector.createFromOptions(context, options).use { detector ->
                detector.detect(image).detections()
                    .mapNotNull { it.categories().firstOrNull()?.categoryName() }
                    .groupingBy { it }.eachCount().let(found::putAll)
            }
        }
        // The print classifier looks at the whole cake, so it also catches prints the detector misses.
        if (PRINT in assets && (found[PhotoSuggest.PRINT] ?: 0) == 0) {
            val options = ImageClassifier.ImageClassifierOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(PRINT).build())
                .setMaxResults(1).build()
            ImageClassifier.createFromOptions(context, options).use { classifier ->
                val top = classifier.classify(image).classificationResult().classifications().firstOrNull()?.categories()?.firstOrNull()
                if (top?.categoryName() == "yes" && top.score() >= PRINT_SCORE) found[PhotoSuggest.PRINT] = 1
            }
        }
        if (TIERS in assets) {
            val options = ImageClassifier.ImageClassifierOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(TIERS).build())
                .setMaxResults(1).build()
            ImageClassifier.createFromOptions(context, options).use { classifier ->
                classifier.classify(image).classificationResult().classifications().firstOrNull()
                    ?.categories()?.firstOrNull()?.categoryName()?.toIntOrNull()
                    ?.let { found[PhotoSuggest.TIERS] = it }
            }
        }
        return found
    }

    companion object {
        const val PARTS = "cake_parts.tflite"
        const val TIERS = "cake_tiers.tflite"
        const val PRINT = "cake_print.tflite"
        const val PRINT_SCORE = 0.6f
        /** Raise if it counts things that aren't toppers, lower if it misses them. */
        const val PART_SCORE = 0.4f
    }
}
