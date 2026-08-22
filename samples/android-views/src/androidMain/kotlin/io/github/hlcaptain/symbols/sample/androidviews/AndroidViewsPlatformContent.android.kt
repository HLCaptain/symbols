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
internal actual fun AndroidViewsPlatformContent() {
    ExampleCard(
        title = "Embedded Android View hierarchy",
        description = "The XML layout keeps View Binding, Data Binding, and a custom ImageView.",
    ) {
        AndroidView(
            factory = { context ->
                AndroidViewsContentBinding.inflate(LayoutInflater.from(context)).apply {
                    viewBindingIcon.setImageResource(
                        OutlinedDrawablesR.drawable.material_symbols_outlined_check_ue5ca,
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
