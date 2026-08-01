package io.github.hlcaptain.symbols.sample

import android.os.Bundle
import android.widget.ImageView
import androidx.activity.ComponentActivity
import io.github.hlcaptain.symbols.material.outlined.drawables.R as OutlinedDrawablesR
import io.github.hlcaptain.symbols.sample.databinding.ActivityLegacyViewsBinding
import io.github.hlcaptain.symbols.sample.databinding.DataBindingIconBinding

class LegacyViewsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityLegacyViewsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.contextDrawableIcon.setImageDrawable(
            getDrawable(
                OutlinedDrawablesR.drawable.material_symbols_outlined_search_ue8b6,
            ),
        )
        binding.viewBindingIcon.setImageResource(
            R.drawable.app_icons_regular_check_ue5ca,
        )
        findViewById<ImageView>(R.id.find_view_by_id_icon).setImageResource(
            OutlinedDrawablesR.drawable.material_symbols_outlined_settings_ue8b8,
        )

        DataBindingIconBinding.inflate(
            layoutInflater,
            binding.dataBindingHost,
            true,
        ).icon = getDrawable(
            OutlinedDrawablesR.drawable.material_symbols_outlined_favorite_ue87e,
        )
    }
}
