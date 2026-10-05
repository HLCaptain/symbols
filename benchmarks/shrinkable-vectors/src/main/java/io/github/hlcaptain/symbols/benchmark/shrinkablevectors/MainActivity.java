package io.github.hlcaptain.symbols.benchmark.shrinkablevectors;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ImageView;
import androidx.compose.ui.graphics.vector.ImageVector;
import io.github.hlcaptain.symbols.Symbols;
import io.github.hlcaptain.symbols.material.IconsKt;
import io.github.hlcaptain.symbols.material.outlined.vectors.OutlinedIcons000_generatedKt;
import io.github.hlcaptain.symbols.material.outlined.vectors.OutlinedIcons011_generatedKt;
import io.github.hlcaptain.symbols.material.outlined.vectors.OutlinedIcons016_generatedKt;
import io.github.hlcaptain.symbols.material.sharp.vectors.SharpIcons000_generatedKt;
import io.github.hlcaptain.symbols.material.sharp.vectors.SharpIcons016_generatedKt;

/**
 * Consumer of ordinary/mirrored Outlined vectors and both new Filled namespaces.
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
        ImageVector outlinedFilled = OutlinedIcons016_generatedKt.getFavorite(
                IconsKt.getFilled(IconsKt.getOutlined(IconsKt.getMaterial(Symbols.INSTANCE))));
        ImageVector outlinedMirroredFilled = OutlinedIcons000_generatedKt.getVolumeOff(
                IconsKt.getFilled(IconsKt.getOutlined(
                        IconsKt.getAutoMirrored(IconsKt.getMaterial(Symbols.INSTANCE)))));
        ImageVector sharpFilled = SharpIcons016_generatedKt.getFavorite(
                IconsKt.getFilled(IconsKt.getSharp(IconsKt.getMaterial(Symbols.INSTANCE))));
        ImageVector sharpMirroredFilled = SharpIcons000_generatedKt.getVolumeOff(
                IconsKt.getFilled(IconsKt.getSharp(
                        IconsKt.getAutoMirrored(IconsKt.getMaterial(Symbols.INSTANCE)))));
        setTitle(check.getName() + ":" + check.getAutoMirror()
                + " | " + arrowBack.getName() + ":" + arrowBack.getAutoMirror()
                + " | " + outlinedFilled.getName() + ":" + outlinedFilled.getAutoMirror()
                + " | " + outlinedMirroredFilled.getName() + ":" + outlinedMirroredFilled.getAutoMirror()
                + " | " + sharpFilled.getName() + ":" + sharpFilled.getAutoMirror()
                + " | " + sharpMirroredFilled.getName() + ":" + sharpMirroredFilled.getAutoMirror());
        ImageView icon = new ImageView(this);
        icon.setImageResource(
                R.drawable.native_benchmark_icons_regular_branch_ue0a0);
        setContentView(icon);
    }
}
