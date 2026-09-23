package net.pokedex.designsystem.theme

import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Motion tokens, already resolved for reduce-motion.
 *
 * The important design choice here is that [PokedexMotion] is handed to call sites in its
 * *final* form. There is no `if (reduceMotion)` for a component to forget: when the user
 * has animations off, every accessor below returns `snap()` or zero, and every animation
 * in the app stops without a single component knowing why.
 *
 * Springs rather than fixed curves for anything the finger drives -- the one thing worth
 * taking wholesale from M3 Expressive. Tweens are kept for what is not interruptible, like
 * a celebration that runs once.
 *
 * The interactive and state springs are exposed as generic functions rather than as typed
 * specs because the same spring animates a `Float` scale and a `Dp` corner radius, and
 * having two tokens that must be kept in sync is how they stop being in sync.
 */
@Immutable
data class PokedexMotion(
    val interactiveSpring: SpringParams,
    val stateSpring: SpringParams,

    /** The catch celebration sweep. Runs once, so it tweens. */
    val celebration: AnimationSpec<Float>,

    /** A box completing. The one moment allowed to take half a second. */
    val flourish: AnimationSpec<Float>,

    /** Screen transitions and sheet entry. */
    val container: FiniteAnimationSpec<Float>,

    /** Skeleton loaders. Zero when reduce-motion is on, which stops the pulse entirely. */
    val skeletonDurationMillis: Int,

    /** True when the user has asked the system for no animation. */
    val reduced: Boolean,
) {
    /** Press, release, selection. Interruptible, so it springs. */
    fun <T> interactive(): FiniteAnimationSpec<T> = interactiveSpring.spec()

    /** The caught rim and pip arriving. Slightly softer, still interruptible. */
    fun <T> stateChange(): FiniteAnimationSpec<T> = stateSpring.spec()
}

@Immutable
data class SpringParams(val dampingRatio: Float, val stiffness: Float, val snap: Boolean) {
    fun <T> spec(): FiniteAnimationSpec<T> =
        if (snap) snap() else spring(dampingRatio = dampingRatio, stiffness = stiffness)
}

/**
 * A single decelerating curve for everything that is not a spring.
 *
 * One easing used consistently reads as more considered than five easings used
 * approximately.
 */
val CaseEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

private const val CELEBRATION_MILLIS = 280
private const val FLOURISH_MILLIS = 520
private const val CONTAINER_MILLIS = 240
private const val SKELETON_MILLIS = 1100

internal val FullMotion = PokedexMotion(
    interactiveSpring = SpringParams(0.55f, Spring.StiffnessMediumLow, snap = false),
    stateSpring = SpringParams(0.7f, Spring.StiffnessMedium, snap = false),
    celebration = tween(CELEBRATION_MILLIS, easing = CaseEasing),
    flourish = tween(FLOURISH_MILLIS, easing = CaseEasing),
    container = tween(CONTAINER_MILLIS, easing = CaseEasing),
    skeletonDurationMillis = SKELETON_MILLIS,
    reduced = false,
)

internal val ReducedMotion = PokedexMotion(
    interactiveSpring = SpringParams(1f, Spring.StiffnessHigh, snap = true),
    stateSpring = SpringParams(1f, Spring.StiffnessHigh, snap = true),
    celebration = snap(),
    flourish = snap(),
    container = snap(),
    skeletonDurationMillis = 0,
    reduced = true,
)

/**
 * Reads the system animation scale.
 *
 * `ANIMATOR_DURATION_SCALE == 0` is what Android's own "Remove animations" accessibility
 * setting and every battery saver set. There is no Compose API for this, so we read the
 * setting directly; it resolves once per theme composition rather than per frame.
 */
@Composable
internal fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}
