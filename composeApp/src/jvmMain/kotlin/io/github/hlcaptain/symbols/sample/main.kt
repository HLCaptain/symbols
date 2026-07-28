package io.github.hlcaptain.symbols.sample

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Symbols",
    ) {
        App()
    }
}
