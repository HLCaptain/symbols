package io.github.hlcaptain.symbols.sample.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
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

@PreviewSymbolsScreen
@Composable
private fun AxisControlsPreview() {
    val defaults = remember {
        mapOf("FILL" to 0f, "wght" to 400f, "GRAD" to 0f, "opsz" to 24f)
    }
    val values = remember {
        mutableStateMapOf<String, Float>().apply { putAll(defaults) }
    }
    MaterialTheme {
        AxisControls(
            axes = listOf(
                AxisUiModel("FILL", "Fill", values.getValue("FILL"), 0f, 1f),
                AxisUiModel("wght", "Weight", values.getValue("wght"), 100f, 700f),
                AxisUiModel("GRAD", "Grade", values.getValue("GRAD"), -50f, 200f),
                AxisUiModel("opsz", "Optical", values.getValue("opsz"), 20f, 48f),
            ),
            onValueChange = values::set,
            onReset = { values.putAll(defaults) },
            resetEnabled = values != defaults,
            title = "Runtime Material font axes",
        )
    }
}
