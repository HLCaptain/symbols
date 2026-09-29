package io.github.hlcaptain.symbols.sample.androidviews

import android.view.LayoutInflater
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.hlcaptain.symbols.material.outlined.drawables.R as OutlinedDrawablesR
import io.github.hlcaptain.symbols.sample.androidviews.databinding.AndroidViewsContentBinding
import io.github.hlcaptain.symbols.sample.androidviews.databinding.DataBindingIconBinding
import io.github.hlcaptain.symbols.sample.ui.ExampleCard

@Composable
fun AndroidViewsContent() {
    ExampleCard(
        title = "Embedded Android View hierarchy",
        description = "The XML layout compares downloaded, font-generated, and SVG-generated drawables.",
    ) {
        AndroidView(
            factory = { context ->
                AndroidViewsContentBinding.inflate(LayoutInflater.from(context)).apply {
                    viewBindingIcon.setImageDrawable(
                        AppCompatResources.getDrawable(
                            context,
                            R.drawable.android_view_icons_regular_branch_ue0a0,
                        ),
                    )
                    DataBindingIconBinding.inflate(
                        LayoutInflater.from(context),
                        dataBindingHost,
                        true,
                    ).icon = AppCompatResources.getDrawable(
                        context,
                        OutlinedDrawablesR.drawable.material_symbols_outlined_favorite_ue87e,
                    )
                }.root
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
