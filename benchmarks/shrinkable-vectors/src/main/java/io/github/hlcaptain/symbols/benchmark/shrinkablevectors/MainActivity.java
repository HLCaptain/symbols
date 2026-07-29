package io.github.hlcaptain.symbols.benchmark.shrinkablevectors;

import android.app.Activity;
import android.os.Bundle;
import androidx.compose.ui.graphics.vector.ImageVector;
import io.github.hlcaptain.symbols.material.Icons;
import io.github.hlcaptain.symbols.material.outlined.vectors.OutlinedIcons011_generatedKt;

/**
 * Minimal consumer of exactly one typed vector getter: Icons.Outlined.Check.
 *
 * Java spells the Kotlin extension property as its generated static getter.
 * Passing Icons.Outlined.INSTANCE is exactly the bytecode call emitted for
 * `Icons.Outlined.Check` in Kotlin.
 */
public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ImageVector check =
                OutlinedIcons011_generatedKt.getCheck(Icons.Outlined.INSTANCE);
        setTitle(check.getName());
    }
}
