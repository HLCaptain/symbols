package io.github.hlcaptain.symbols.sample

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(
                onOpenLegacyViews = {
                    startActivity(Intent(this, LegacyViewsActivity::class.java))
                },
            )
        }
    }
}
