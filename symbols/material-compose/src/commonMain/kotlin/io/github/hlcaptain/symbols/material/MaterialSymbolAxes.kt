package io.github.hlcaptain.symbols.material

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.FontVariation
import io.github.hlcaptain.symbols.font.SymbolFontAxis
import io.github.hlcaptain.symbols.font.SymbolFontSettings

/**
 * Selects the appearance of a Material Symbols variable font.
 *
 * [fill] moves between an outline and a filled symbol. [weight] controls stroke thickness, [grade]
 * provides a smaller visual adjustment separate from weight, and [opticalSize] selects the amount
 * of detail intended for the rendered size. The accepted ranges come from the bundled Material
 * Symbols 2.874 variable fonts.
 *
 * Construction validates the values and creates matching [fontSettings]. It does not load or
 * render a font.
 *
 * @property fill outline-to-filled value from [MinFill] to [MaxFill]
 * @property weight stroke weight from [MinWeight] to [MaxWeight]
 * @property grade fine weight adjustment from [MinGrade] to [MaxGrade]
 * @property opticalSize intended symbol size from [MinOpticalSize] to [MaxOpticalSize]
 * @throws IllegalArgumentException if a value is not finite or is outside its accepted range
 */
@Immutable
data class MaterialSymbolAxes(
    val fill: Float = DefaultFill,
    val weight: Int = DefaultWeight,
    val grade: Float = DefaultGrade,
    val opticalSize: Float = DefaultOpticalSize,
) {
    init {
        require(fill.isFinite() && fill in MinFill..MaxFill) {
            "fill must be finite and in $MinFill..$MaxFill, but was $fill"
        }
        require(weight in MinWeight..MaxWeight) {
            "weight must be in $MinWeight..$MaxWeight, but was $weight"
        }
        require(grade.isFinite() && grade in MinGrade..MaxGrade) {
            "grade must be finite and in $MinGrade..$MaxGrade, but was $grade"
        }
        require(opticalSize.isFinite() && opticalSize in MinOpticalSize..MaxOpticalSize) {
            "opticalSize must be finite and in $MinOpticalSize..$MaxOpticalSize, but was $opticalSize"
        }
    }

    /** These Material coordinates as generic symbol-font settings. */
    val fontSettings: SymbolFontSettings =
        SymbolFontSettings(
            variationSettings = FontVariation.Settings(
                FontVariation.Setting("FILL", fill),
                FontVariation.Setting("GRAD", grade),
                FontVariation.Setting("opsz", opticalSize),
                FontVariation.weight(weight),
            ),
        )

    companion object {
        /** The immutable default point used by Material Symbols fonts and themes. */
        val Default: MaterialSymbolAxes = MaterialSymbolAxes()

        const val MinFill: Float = 0f
        const val DefaultFill: Float = 0f
        const val MaxFill: Float = 1f

        const val MinWeight: Int = 100
        const val DefaultWeight: Int = 400
        const val MaxWeight: Int = 700

        const val MinGrade: Float = -50f
        const val DefaultGrade: Float = 0f
        const val MaxGrade: Float = 200f

        const val MinOpticalSize: Float = 20f
        const val DefaultOpticalSize: Float = 24f
        const val MaxOpticalSize: Float = 48f

        /** Axis metadata embedded by every bundled Material variable font. */
        val VariationAxes: List<SymbolFontAxis> = listOf(
            SymbolFontAxis("FILL", MinFill, DefaultFill, MaxFill, "Fill"),
            SymbolFontAxis(
                "wght",
                MinWeight.toFloat(),
                DefaultWeight.toFloat(),
                MaxWeight.toFloat(),
                "Weight",
            ),
            SymbolFontAxis("GRAD", MinGrade, DefaultGrade, MaxGrade, "Grade"),
            SymbolFontAxis(
                "opsz",
                MinOpticalSize,
                DefaultOpticalSize,
                MaxOpticalSize,
                "Optical",
            ),
        )
    }
}
