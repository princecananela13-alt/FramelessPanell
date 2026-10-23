package com.frameless.panel;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    private Prefs() {}

    static final int[] OPACITY = {60, 75, 90, 100};
    static final String[] SIZE_NAMES = {"S", "M", "L"};

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("panel", Context.MODE_PRIVATE);
    }

    static String side(Context c) {
        return sp(c).getString("side", "auto");
    }

    static int size(Context c) {
        return Math.max(0, Math.min(2, sp(c).getInt("size", 1)));
    }

    static int opacity(Context c) {
        return sp(c).getInt("op", 90);
    }

    static void cycleSide(Context c) {
        String s = side(c);
        String n = "auto".equals(s) ? "r" : "r".equals(s) ? "l" : "auto";
        sp(c).edit().putString("side", n).apply();
    }

    static void cycleSize(Context c) {
        sp(c).edit().putInt("size", (size(c) + 1) % 3).apply();
    }

    static void cycleOpacity(Context c) {
        int cur = opacity(c);
        int next = OPACITY[0];
        for (int i = 0; i < OPACITY.length; i++) {
            if (OPACITY[i] == cur) {
                next = OPACITY[(i + 1) % OPACITY.length];
                break;
            }
        }
        sp(c).edit().putInt("op", next).apply();
    }

    static String sideLabel(Context c) {
        String s = side(c);
        return "Side: " + ("l".equals(s) ? "Left" : "r".equals(s) ? "Right" : "Auto");
    }

    static String sizeLabel(Context c) {
        return "Size: " + SIZE_NAMES[size(c)];
    }

    static String opacityLabel(Context c) {
        return "Opacity: " + opacity(c) + "%";
    }
  }
