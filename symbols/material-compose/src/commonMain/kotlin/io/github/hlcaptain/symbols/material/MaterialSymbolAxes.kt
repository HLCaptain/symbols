package io.github.hlcaptain.symbols.material

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/**
 * A point in the four-axis Material Symbols design space.
 *
 * The ranges come from the bundled Material Symbols 2.874 variable fonts.
 */
@Immutable
public data class MaterialSymbolAxes(
    public val fill: Float = DefaultFill,
    public val weight: Int = DefaultWeight,
    public val grade: Float = DefaultGrade,
    public val opticalSize: Float = DefaultOpticalSize,
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

    internal val fontWeight: FontWeight
        get() = FontWeight(weight)

    internal fun variationSettings(): FontVariation.Settings =
        FontVariation.Settings(
            FontVariation.Setting("FILL", fill),
            FontVariation.Setting("GRAD", grade),
            FontVariation.Setting("opsz", opticalSize),
            FontVariation.weight(weight),
        )

    public companion object {
        /** The immutable default point used by Material Symbols fonts and themes. */
        public val Default: MaterialSymbolAxes = MaterialSymbolAxes()

        public const val MinFill: Float = 0f
        public const val DefaultFill: Float = 0f
        public const val MaxFill: Float = 1f

        public const val MinWeight: Int = 100
        public const val DefaultWeight: Int = 400
        public const val MaxWeight: Int = 700

        public const val MinGrade: Float = -50f
        public const val DefaultGrade: Float = 0f
        public const val MaxGrade: Float = 200f

        public const val MinOpticalSize: Float = 20f
        public const val DefaultOpticalSize: Float = 24f
        public const val MaxOpticalSize: Float = 48f
    }
}
