package example;

import static io.github.hlcaptain.symbols.material.outlined.drawables.automirrored.filled.R.drawable.*;
import static io.github.hlcaptain.symbols.material.rounded.drawables.automirrored.filled.R.drawable.*;
import static io.github.hlcaptain.symbols.material.sharp.drawables.automirrored.filled.R.drawable.*;

public final class ConsumerActivity extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        android.widget.LinearLayout images = new android.widget.LinearLayout(this);
        for (int resource : new int[] {
            material_symbols_automirrored_outlined_filled_volume_off_ue04f,
            material_symbols_automirrored_rounded_filled_volume_off_ue04f,
            material_symbols_automirrored_sharp_filled_volume_off_ue04f,
        }) {
            android.widget.ImageView image = new android.widget.ImageView(this);
            image.setImageResource(resource);
            images.addView(image);
        }
        setContentView(images);
    }
}
