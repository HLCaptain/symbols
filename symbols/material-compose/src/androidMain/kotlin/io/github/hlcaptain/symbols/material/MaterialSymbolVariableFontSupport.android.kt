package io.github.hlcaptain.symbols.material

import android.os.Build

internal actual fun platformSupportsMaterialSymbolVariableFonts(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
