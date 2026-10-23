package com.frameless.panel;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

final class Bus {
    private Bus() {}

    static void send(Context c, String cmd) {
        try (FileOutputStream o = new FileOutputStream(new File(c.getFilesDir(), "cmd"), true)) {
            o.write((cmd + "\n").getBytes("UTF-8"));
        } catch (Exception ignored) {
        }
    }

    static Map<String, String> read(Context c) {
        Map<String, String> m = new HashMap<>();
        File f = new File(c.getFilesDir(), "status");
        if (!f.exists()) return m;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
            String l;
            while ((l = br.readLine()) != null) {
                int i = l.indexOf('=');
                if (i > 0) m.put(l.substring(0, i), l.substring(i + 1));
            }
        } catch (Exception ignored) {
        }
        return m;
    }
}
