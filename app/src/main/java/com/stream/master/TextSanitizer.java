package com.stream.master;

import java.nio.charset.StandardCharsets;

/**
 * Repairs common UTF-8-as-Latin-1/Windows-1252 mojibake returned by legacy
 * movie metadata sources. Correct Unicode text is returned unchanged.
 */
public final class TextSanitizer {
    private TextSanitizer() {}

    public static String clean(String value) {
        if (value == null || value.isEmpty()) return value;

        String current = value;
        // A few metadata sources can be double-encoded, so allow two passes.
        for (int i = 0; i < 2; i++) {
            if (!looksMojibaked(current)) break;
            try {
                String repaired = new String(
                        current.getBytes(StandardCharsets.ISO_8859_1),
                        StandardCharsets.UTF_8
                );
                if (repaired.indexOf('\uFFFD') >= 0 || repaired.equals(current)) break;
                current = repaired;
            } catch (Exception ignored) {
                break;
            }
        }
        return current;
    }

    private static boolean looksMojibaked(String value) {
        return value.indexOf('\u00C3') >= 0   // Ã
                || value.indexOf('\u00C2') >= 0 // Â
                || value.indexOf('\u00E2') >= 0 // â
                || value.indexOf('\u00F0') >= 0 // ð
                || value.indexOf('\u0092') >= 0
                || value.indexOf('\u0093') >= 0
                || value.indexOf('\u0094') >= 0
                || value.indexOf('\u0099') >= 0;
    }
}
