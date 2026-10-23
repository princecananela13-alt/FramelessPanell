package com.frameless.panel;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;
    private Button bSide, bSize, bOp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int p = Math.round(20 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(p, p * 2, p, p);
        root.setBackgroundColor(0xFF0E0B16);

        TextView title = new TextView(this);
        title.setText("Frameless Panel");
        title.setTextColor(0xFFEDE9FE);
        title.setTextSize(24);
        title.setGravity(Gravity.START);
        root.addView(title);

        TextView info = new TextView(this);
        info.setText("In-game panel for the Frameless_AI module.\n\nTurn it on in the module WebUI > Games > Game panel. "
                + "The panel only appears while a game from \"My games\" is open: swipe from the screen edge to open it.");
        info.setTextColor(0xFF9A8FB5);
        info.setPadding(0, p / 2, 0, p);
        root.addView(info);

        status = new TextView(this);
        status.setTextColor(0xFFA78BFA);
        status.setPadding(0, 0, 0, p);
        root.addView(status);

        root.addView(button("Allow display over other apps", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            }
        }));
        root.addView(button("Preview panel", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, PanelService.class).putExtra("pkg", getPackageName());
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
                else startService(i);
            }
        }));
        root.addView(button("Hide panel", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService(new Intent(MainActivity.this, PanelService.class));
            }
        }));

        TextView look = new TextView(this);
        look.setText("Panel look (tap preview again to apply)");
        look.setTextColor(0xFF9A8FB5);
        look.setPadding(0, p, 0, 0);
        root.addView(look);

        bSide = button("", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.cycleSide(MainActivity.this);
                labels();
            }
        });
        bSize = button("", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.cycleSize(MainActivity.this);
                labels();
            }
        });
        bOp = button("", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.cycleOpacity(MainActivity.this);
                labels();
            }
        });
        root.addView(bSide);
        root.addView(bSize);
        root.addView(bOp);
        labels();
        setContentView(root);
    }

    private void labels() {
        bSide.setText(Prefs.sideLabel(this));
        bSize.setText(Prefs.sizeLabel(this));
        bOp.setText(Prefs.opacityLabel(this));
    }

    private Button button(String t, View.OnClickListener l) {
        Button x = new Button(this);
        x.setText(t);
        x.setAllCaps(false);
        x.setOnClickListener(l);
        return x;
    }

    @Override
    protected void onResume() {
        super.onResume();
        status.setText(Settings.canDrawOverlays(this)
                ? "Overlay permission: allowed"
                : "Overlay permission: NOT allowed yet (the module also grants it at boot)");
    }
                     }
