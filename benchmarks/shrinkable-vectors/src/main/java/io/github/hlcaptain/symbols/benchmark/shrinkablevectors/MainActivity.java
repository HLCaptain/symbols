package io.github.hlcaptain.symbols.benchmark.shrinkablevectors;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ImageView;
import androidx.compose.ui.graphics.vector.ImageVector;
import io.github.hlcaptain.symbols.Symbols;
import io.github.hlcaptain.symbols.material.IconsKt;
import io.github.hlcaptain.symbols.material.outlined.vectors.OutlinedIcons011_generatedKt;

/**
 * Minimal consumer of two typed getters: ordinary Check and mirrored ArrowBack.
 *
 * Java spells the Kotlin extension property as its generated static getter.
 * The nested static calls are the bytecode emitted for
 * `Symbols.Material.Outlined.Check` and
 * `Symbols.Material.AutoMirrored.Outlined.ArrowBack` in Kotlin.
 */
public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ImageVector check =
                OutlinedIcons011_generatedKt.getCheck(
                        IconsKt.getOutlined(IconsKt.getMaterial(Symbols.INSTANCE)));
        ImageVector arrowBack =
                OutlinedIcons011_generatedKt.getArrowBack(
                        IconsKt.getOutlined(
                                IconsKt.getAutoMirrored(IconsKt.getMaterial(Symbols.INSTANCE))));
        setTitle(check.getName() + ":" + check.getAutoMirror()
                + " | " + arrowBack.getName() + ":" + arrowBack.getAutoMirror());
        ImageView icon = new ImageView(this);
        icon.setImageResource(
                R.drawable.native_benchmark_icons_regular_branch_ue0a0);
        setContentView(icon);
    }
}
