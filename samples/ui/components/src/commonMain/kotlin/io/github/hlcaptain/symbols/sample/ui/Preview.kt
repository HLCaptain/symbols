package io.github.hlcaptain.symbols.sample.ui

import androidx.compose.ui.tooling.preview.Preview

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PreviewScreenshotBaseline

@Preview(
    name = "Compact",
    widthDp = 400,
    heightDp = 800,
    showBackground = true,
)
@Preview(
    name = "Expanded",
    widthDp = 1000,
    heightDp = 700,
    showBackground = true,
)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PreviewSymbolsScreen
