package io.github.hlcaptain.symbols.font

import android.os.Build

internal actual fun platformSupportsVariableFonts(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
