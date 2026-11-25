package ru.mesh.expressive.ui.components

import android.graphics.Path
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Точный порт физики Material 3 Predictive Back из exteraGram / exteraless (PredictiveBackAnimationHelper).
 */
object PredictiveBackPhysics {

    private val gesturePath = PathInterpolator(0.1f, 0.1f, 0.0f, 1.0f)

    private val emphasizedPath = Path().apply {
        moveTo(0.0f, 0.0f)
        cubicTo(0.05f, 0.0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
        cubicTo(0.208333f, 0.82f, 0.25f, 1.0f, 1.0f, 1.0f)
    }
    private val postCommitPath = PathInterpolator(emphasizedPath)
    private val verticalInterpolator = DecelerateInterpolator()

    val postCommitEasing: Easing = Easing { fraction ->
        postCommitPath.getInterpolation(fraction.coerceIn(0f, 1f))
    }

    // EASE_OUT_QUINT для отмены свайпа
    val cancelEasing: Easing = CubicBezierEasing(0.23f, 1.0f, 0.32f, 1.0f)

    const val POST_COMMIT_DURATION_MS = 375
    const val SPRING_STIFFNESS = 900f
    const val SPRING_DAMPING = 1.0f // Spring.DampingRatioNoBouncy
    const val BACK_PARALLAX_FACTOR = 0.35f

    fun cancelDurationMs(startProgress: Float): Int =
        max(120, (startProgress.coerceIn(0f, 1f) * 220f).toInt())

    fun interpolateGesture(rawProgress: Float): Float =
        gesturePath.getInterpolation(rawProgress.coerceIn(0f, 1f))

    fun computeYOffsetPx(
        containerHeightPx: Float,
        currentRectHeightPx: Float,
        dyPx: Float,
        dp8Px: Float
    ): Float {
        val half = containerHeightPx / 2f
        if (half <= 0f) return 0f
        val sign = if (dyPx < 0f) -1f else 1f
        val t = verticalInterpolator.getInterpolation(min(half, abs(dyPx)) / half)
        val maxOffset = max(0f, (containerHeightPx - currentRectHeightPx) / 2f - dp8Px)
        return sign * t * maxOffset
    }

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
