package knaufdan.android.arch.utils

import android.content.Context
import android.content.ContextWrapper
import android.util.TypedValue
import androidx.lifecycle.LifecycleOwner

internal tailrec fun Context?.findLifecycleOwner(): LifecycleOwner? =
    if (this is LifecycleOwner) this
    else (this as? ContextWrapper)?.baseContext?.findLifecycleOwner()

fun Context.dpToPx(dp: Number): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics)