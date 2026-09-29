package io.github.hlcaptain.symbols.sample.androidviews

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import io.github.hlcaptain.symbols.material.outlined.drawables.R as OutlinedDrawablesR

class GeneratedSymbolView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {
    init {
        val attributes = context.obtainStyledAttributes(
            attrs,
            R.styleable.GeneratedSymbolView,
        )
        try {
            setImageResource(
                attributes.getResourceId(
                    R.styleable.GeneratedSymbolView_symbolDrawable,
                    OutlinedDrawablesR.drawable.material_symbols_outlined_home_ue9b2,
                ),
            )
        } finally {
            attributes.recycle()
        }
        scaleType = ScaleType.CENTER_INSIDE
    }
}
