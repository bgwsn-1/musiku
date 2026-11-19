package app.musiku.ui.lyrics.flamingo

import android.animation.ValueAnimator
import android.os.Build

/** [ValueAnimator.areAnimatorsEnabled] is API 26; minSdk is 24, so guard the lookup. */
internal fun areAnimatorsEnabled(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ValueAnimator.areAnimatorsEnabled()
    } else {
        true
    }
