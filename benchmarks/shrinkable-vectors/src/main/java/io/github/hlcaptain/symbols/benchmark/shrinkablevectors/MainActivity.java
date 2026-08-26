package io.github.hlcaptain.symbols.benchmark.shrinkablevectors;

import android.app.Activity;
import android.os.Bundle;
import androidx.compose.ui.graphics.vector.ImageVector;
import io.github.hlcaptain.symbols.Symbols;
import io.github.hlcaptain.symbols.material.IconsKt;
import io.github.hlcaptain.symbols.material.outlined.vectors.OutlinedIcons011_generatedKt;

/**
 * Minimal consumer of exactly one typed vector getter: Symbols.Material.Outlined.Check.
 *
 * Java spells the Kotlin extension property as its generated static getter.
 * The nested static calls are the bytecode emitted for
 * `Symbols.Material.Outlined.Check` in Kotlin.
 */
public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ImageVector check =
                OutlinedIcons011_generatedKt.getCheck(
                        IconsKt.getOutlined(IconsKt.getMaterial(Symbols.INSTANCE)));
        setTitle(check.getName());
    }
}
