package example;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static io.github.hlcaptain.symbols.material.outlined.drawables.R.drawable.*;
import static io.github.hlcaptain.symbols.material.outlined.drawables.filled.R.drawable.*;
import static io.github.hlcaptain.symbols.material.outlined.drawables.automirrored.R.drawable.*;
import static io.github.hlcaptain.symbols.material.outlined.drawables.automirrored.filled.R.drawable.*;
import static io.github.hlcaptain.symbols.material.rounded.drawables.R.drawable.*;
import static io.github.hlcaptain.symbols.material.rounded.drawables.filled.R.drawable.*;
import static io.github.hlcaptain.symbols.material.rounded.drawables.automirrored.R.drawable.*;
import static io.github.hlcaptain.symbols.material.rounded.drawables.automirrored.filled.R.drawable.*;
import static io.github.hlcaptain.symbols.material.sharp.drawables.R.drawable.*;
import static io.github.hlcaptain.symbols.material.sharp.drawables.filled.R.drawable.*;
import static io.github.hlcaptain.symbols.material.sharp.drawables.automirrored.R.drawable.*;
import static io.github.hlcaptain.symbols.material.sharp.drawables.automirrored.filled.R.drawable.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MirroringTest {
    private static final int SIZE = 144;

    private int[] render(int resource, boolean rtl, boolean mirrored, String filename) throws Exception {
        View host = LayoutInflater.from(RuntimeEnvironment.getApplication())
            .inflate(example.consumer.R.layout.native_drawable, null);
        ImageView image = host.findViewById(example.consumer.R.id.symbol);
        // Exercise the XML android:src directly for this variant.
        if (resource != material_symbols_automirrored_rounded_filled_volume_off_ue04f) {
            image.setImageResource(resource);
        }
        int direction = rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR;
        host.setLayoutDirection(direction);
        int measureSpec = View.MeasureSpec.makeMeasureSpec(SIZE, View.MeasureSpec.EXACTLY);
        host.measure(measureSpec, measureSpec);
        host.layout(0, 0, SIZE, SIZE);
        Drawable drawable = image.getDrawable();
        assertEquals(mirrored, drawable.isAutoMirrored());
        assertEquals("ImageView inherits host direction", direction, image.getLayoutDirection());
        assertEquals("Drawable inherits ImageView direction", direction, drawable.getLayoutDirection());
        Bitmap bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        host.draw(new Canvas(bitmap));
        int[] pixels = new int[SIZE * SIZE];
        bitmap.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE);
        assertTrue("Drawable must render ink", Arrays.stream(pixels).anyMatch(p -> p != 0));

        File output = new File(System.getProperty("renderOutput"), filename + ".png");
        output.getParentFile().mkdirs();
        Bitmap preview = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas previewCanvas = new Canvas(preview);
        previewCanvas.drawColor(android.graphics.Color.WHITE);
        previewCanvas.drawBitmap(bitmap, 0, 0, null);
        try (FileOutputStream stream = new FileOutputStream(output)) {
            assertTrue(preview.compress(Bitmap.CompressFormat.PNG, 100, stream));
        }
        return pixels;
    }

    private int[] verifyPair(int ordinary, int mirrored, String label) throws Exception {
        int[] normalLtr = render(ordinary, false, false, label + "-ltr");
        int[] normalRtl = render(ordinary, true, false, label + "-rtl");
        int[] autoLtr = render(mirrored, false, true, label + "-auto-ltr");
        int[] autoRtl = render(mirrored, true, true, label + "-auto-rtl");
        assertArrayEquals("Ordinary resource stays fixed in RTL", normalLtr, normalRtl);
        assertArrayEquals("Mirroring preserves the LTR shape", normalLtr, autoLtr);
        assertFalse("Asymmetric resource changes in RTL", Arrays.equals(autoLtr, autoRtl));
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int expected = normalLtr[y * SIZE + SIZE - 1 - x] >>> 24;
                int actual = autoRtl[y * SIZE + x] >>> 24;
                // Allow antialiasing roundoff at reflected edges of these black vectors.
                assertTrue(label + ": RTL must reflect LTR at " + x + "," + y,
                    Math.abs(expected - actual) <= 2);
            }
        }
        return normalLtr;
    }

    private void verifyStyle(String style, int ordinary, int mirrored, int filled, int mirroredFilled)
            throws Exception {
        int[] regularPixels = verifyPair(ordinary, mirrored, style + "-regular");
        int[] filledPixels = verifyPair(filled, mirroredFilled, style + "-filled");
        assertFalse(style + ": Filled geometry must differ", Arrays.equals(regularPixels, filledPixels));
    }

    @Test public void outlined() throws Exception {
        verifyStyle("outlined", material_symbols_outlined_volume_off_ue04f,
            material_symbols_automirrored_outlined_volume_off_ue04f,
            material_symbols_outlined_filled_volume_off_ue04f,
            material_symbols_automirrored_outlined_filled_volume_off_ue04f);
    }

    @Test public void rounded() throws Exception {
        verifyStyle("rounded", material_symbols_rounded_volume_off_ue04f,
            material_symbols_automirrored_rounded_volume_off_ue04f,
            material_symbols_rounded_filled_volume_off_ue04f,
            material_symbols_automirrored_rounded_filled_volume_off_ue04f);
    }

    @Test public void sharp() throws Exception {
        verifyStyle("sharp", material_symbols_sharp_volume_off_ue04f,
            material_symbols_automirrored_sharp_volume_off_ue04f,
            material_symbols_sharp_filled_volume_off_ue04f,
            material_symbols_automirrored_sharp_filled_volume_off_ue04f);
    }
}
