package com.maxilu.taptap;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class ArtLoader {
    private ArtLoader() {}

    static Bitmap loadBase64Chunks(Context context, String prefix, int count) {
        try {
            StringBuilder encoded = new StringBuilder();
            byte[] buffer = new byte[8192];

            for (int i = 0; i < count; i++) {
                try (InputStream in = context.getAssets().open(prefix + i + ".b64");
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    int read;
                    while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                    encoded.append(out.toString(StandardCharsets.UTF_8.name()).trim());
                }
            }

            byte[] imageBytes = Base64.decode(encoded.toString(), Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        } catch (Exception ignored) {
            return null;
        }
    }
}
