package com.frameless.panel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/**
 * Draws the in-game panel as a system overlay.
 *  - a thin handle on the upper screen edge (visible while the service runs)
 *  - swipe it inward (or tap it) -> a compact panel opens next to it
 *  - tap anywhere outside the panel -> it closes (the tap still reaches the game)
 * The Frameless_AI module starts this service when a game from "My games" comes to the front
 * and stops it when you leave the game.
 *
 * Commands sent to the module (Bus): prof, dnd, kill, shot, ping, byp, bright N, rec 0|1.
 * Status keys read from the module: bat, temp, ram, prof, dnd, byp, msg, fps, cpu, gpu, bright, rec, ping.
 */
public class PanelService extends Service {
    static final int ACC = 0xFF8B5CF6;
    static final int ACC2 = 0xFFA78BFA;
    static final int TXT = 0xFFEDE9FE;
    static final int MUT = 0xFF9A8FB5;
    static final int OK = 0xFF34D399;
    static final int REC = 0xFFF09595;

    private static final int[] PANEL_W_DP = {200, 226, 250};

    private WindowManager wm;
    private final Handler h = new Handler(Looper.getMainLooper());
    private float density = 1f;

    private View handle;
    private ScrollView panelRoot;
    private LinearLayout panelCard;
    private LinearLayout settingsRow;
    private int panelW;

    private boolean right = true;
    private boolean autoRight = true;
    private boolean open = false;
    private boolean settingsShown = false;
    private boolean dragging = false;
    private String pkg = "";
    private String label = "Game";

    private TextView tvName, tvFps, tvCpu, tvGpu, tvTemp, tvBat, tvMsg, tvBright;
    private TextView tileDnd, tileByp, tileRec, tilePing;
    private SeekBar seek;
    private final TextView[] chips = new TextView[3];
    private final String[] profIds = {"gaming", "boost", "balanced"};
    private final String[] profNames = {"Arena", "Surge", "Everyday"};
    private boolean dndOn = false, bypOn = false, recOn = false;
    private boolean pingWait = false;
    private String lastPing = "";
    private long pingSince = 0;

    // ------------------------------------------------------------------ lifecycle

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        density = getResources().getDisplayMetrics().density;
        getFilesDir(); // make sure the folder exists for the module's status/cmd files
    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        startFg();
        if (i != null) {
            String p = i.getStringExtra("pkg");
            if (p != null) pkg = p;
            String s = i.getStringExtra("side");
            if (s != null) autoRight = !"l".equals(s);
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        label = resolveLabel(pkg);
        rebuild(false);
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        rebuild(false);
    }

    @Override
    public void onDestroy() {
        h.removeCallbacksAndMessages(null);
        open = false;
        removeView(panelRoot);
        removeView(handle);
        handle = null;
        super.onDestroy();
    }

    private void startFg() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel("panel", "Game panel", NotificationManager.IMPORTANCE_MIN);
                nm.createNotificationChannel(ch);
            }
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, "panel")
                    : new Notification.Builder(this);
            b.setSmallIcon(android.R.drawable.ic_menu_manage)
                    .setContentTitle("Frameless Game Panel")
                    .setContentText("Swipe from the screen edge to open");
            startForeground(1, b.build());
        } catch (Exception ignored) {
        }
    }

    private String resolveLabel(String p) {
        if (p == null || p.isEmpty() || p.equals(getPackageName())) return "Preview";
        try {
            PackageManager pm = getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(p, 0)));
        } catch (Exception e) {
            return p;
        }
    }

    /** Rebuild handle + panel (new game, new side/size/opacity, rotation). Keeps the panel open if asked. */
    private void rebuild(boolean reopen) {
        closePanel(false);
        panelRoot = null;
        right = wantRight();
        buildHandle();
        if (reopen) openPanel();
    }

    private boolean wantRight() {
        String s = Prefs.side(this);
        if ("l".equals(s)) return false;
        if ("r".equals(s)) return true;
        return autoRight;
    }

    // ------------------------------------------------------------------ helpers

    private int dp(float v) {
        return Math.round(v * density);
    }

    private DisplayMetrics real() {
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        return dm;
    }

    private int overlayType() {
        return Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private void removeView(View v) {
        if (v == null) return;
        try {
            if (v.getParent() != null) wm.removeViewImmediate(v);
        } catch (Exception ignored) {
        }
    }

    private int alpha() {
        return Math.max(0, Math.min(255, Prefs.opacity(this) * 255 / 100));
    }

    private int bg() {
        return (alpha() << 24) | 0x120D1C;
    }

    private int card() {
        return (alpha() << 24) | 0x1E1730;
    }

    private GradientDrawable round(int color, float radiusDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) d.setStroke(Math.max(1, dp(1)), strokeColor);
        return d;
    }

    private TextView tv(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setIncludeFontPadding(false);
        return t;
    }

    // ------------------------------------------------------------------ edge handle

    private void buildHandle() {
        removeView(handle);
        FrameLayout strip = new FrameLayout(this);
        View bar = new View(this);
        bar.setBackground(round(0x99FFFFFF, 3, 0));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(4), dp(46));
        bp.gravity = Gravity.CENTER_VERTICAL | (right ? Gravity.RIGHT : Gravity.LEFT);
        bp.leftMargin = dp(3);
        bp.rightMargin = dp(3);
        strip.addView(bar, bp);

        strip.setOnTouchListener(new EdgeTouch());
        if (Build.VERSION.SDK_INT >= 29) {
            strip.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int orr, int ob) {
                    v.setSystemGestureExclusionRects(Collections.singletonList(new Rect(0, 0, r - l, b - t)));
                }
            });
        }

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                dp(26), dp(110), overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | (right ? Gravity.RIGHT : Gravity.LEFT);
        lp.y = Math.round(real().heightPixels * 0.10f); // upper part of the side edge
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        try {
            wm.addView(strip, lp);
            handle = strip;
        } catch (Exception e) {
            handle = null;
        }
    }

    private class EdgeTouch implements View.OnTouchListener {
        private float x0;
        private boolean fired;

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    x0 = e.getRawX();
                    fired = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float d = e.getRawX() - x0;
                    if (!fired && (right ? d < -dp(16) : d > dp(16))) {
                        fired = true;
                        openPanel();
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    if (!fired && Math.abs(e.getRawX() - x0) < dp(6)) openPanel(); // a tap works too
                    return true;
                default:
                    return true;
            }
        }
    }

    // ------------------------------------------------------------------ panel building

    private TextView stat(LinearLayout parent, String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        TextView a = tv(title, 9, MUT, true);
        a.setLetterSpacing(0.06f);
        TextView b = tv("--", 13, TXT, true);
        b.setPadding(0, dp(3), 0, 0);
        c.addView(a);
        c.addView(b);
        parent.addView(c, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return b;
    }

    private TextView tile(String text) {
        TextView t = tv(text, 11, TXT, true);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(dp(2), 0, dp(2), 0);
        t.setBackground(round(card(), 8, 0x33A78BFA));
        return t;
    }

    private void setOn(TextView t, boolean on) {
        t.setBackground(round(on ? 0x888B5CF6 : card(), 8, on ? ACC : 0x33A78BFA));
        t.setTextColor(on ? 0xFFFFFFFF : TXT);
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private LinearLayout.LayoutParams cell(int heightDp, boolean gapRight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(heightDp), 1f);
        if (gapRight) p.rightMargin = dp(5);
        return p;
    }

    private LinearLayout.LayoutParams fullRow(int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(topDp);
        return p;
    }

    private void buildPanel() {
        DisplayMetrics dm = real();
        panelW = Math.min(dp(PANEL_W_DP[Prefs.size(this)]), dm.widthPixels - dp(24));

        panelCard = new LinearLayout(this);
        LinearLayout card = panelCard;
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(9), dp(10), dp(9));

        // header
        LinearLayout head = row();
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView t1 = tv("GAME PANEL", 9, ACC2, true);
        t1.setLetterSpacing(0.12f);
        tvName = tv(label, 13, TXT, true);
        tvName.setSingleLine(true);
        tvName.setEllipsize(TextUtils.TruncateAt.END);
        tvName.setPadding(0, dp(2), 0, 0);
        titles.addView(t1);
        titles.addView(tvName);
        head.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView x = tv("\u2715", 14, MUT, false);
        x.setPadding(dp(10), dp(4), dp(2), dp(4));
        x.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closePanel(true);
            }
        });
        head.addView(x);
        card.addView(head);

        // live stats strip: FPS / CPU / GPU / TEMP / BAT
        LinearLayout stats = row();
        stats.setBackground(round(card(), 9, 0));
        stats.setPadding(0, dp(6), 0, dp(6));
        tvFps = stat(stats, "FPS");
        tvCpu = stat(stats, "CPU");
        tvGpu = stat(stats, "GPU");
        tvTemp = stat(stats, "TEMP");
        tvBat = stat(stats, "BAT");
        card.addView(stats, fullRow(7));

        // profile
        LinearLayout pr = row();
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView c = tv(profNames[i], 11, TXT, true);
            c.setGravity(Gravity.CENTER);
            c.setBackground(round(card(), 8, 0x33A78BFA));
            c.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Bus.send(PanelService.this, "prof " + profIds[idx]);
                    highlight(profIds[idx]);
                    tvMsg.setText("Switching profile\u2026");
                }
            });
            chips[i] = c;
            pr.addView(c, cell(30, i < 2));
        }
        card.addView(pr, fullRow(7));

        // brightness
        LinearLayout br = row();
        br.setGravity(Gravity.CENTER_VERTICAL);
        br.addView(tv("\u2600", 14, MUT, false));
        seek = new SeekBar(this);
        seek.setMax(100);
        seek.setProgress(60);
        seek.setProgressTintList(ColorStateList.valueOf(ACC));
        seek.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF2B2146));
        seek.setThumbTintList(ColorStateList.valueOf(TXT));
        tvBright = tv("60%", 11, TXT, true);
        tvBright.setMinWidth(dp(30));
        tvBright.setGravity(Gravity.END);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                tvBright.setText(p + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
                dragging = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                dragging = false;
                Bus.send(PanelService.this, "bright " + Math.max(1, s.getProgress()));
            }
        });
        LinearLayout.LayoutParams sbp = new LinearLayout.LayoutParams(0, dp(28), 1f);
        sbp.leftMargin = dp(2);
        br.addView(seek, sbp);
        br.addView(tvBright);
        card.addView(br, fullRow(5));

        // quick actions (3 x 2)
        tileDnd = tile("DND");
        TextView tRam = tile("Clean RAM");
        TextView tShot = tile("Shot");
        tileRec = tile("Record");
        tilePing = tile("Ping");
        tileByp = tile("Pause chg");

        tileDnd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dndOn = !dndOn;
                setOn(tileDnd, dndOn);
                Bus.send(PanelService.this, "dnd " + (dndOn ? 1 : 0));
            }
        });
        tRam.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Bus.send(PanelService.this, "kill");
                tvMsg.setText("Cleaning\u2026");
            }
        });
        tShot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closePanel(false); // so the panel is not in the picture
                Bus.send(PanelService.this, "shot");
            }
        });
        tileRec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recOn = !recOn;
                showRec();
                Bus.send(PanelService.this, "rec " + (recOn ? 1 : 0));
                if (recOn) closePanel(true); // keep the panel out of the video
            }
        });
        tilePing.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pingWait = true;
                pingSince = System.currentTimeMillis();
                tilePing.setText("Pinging\u2026");
                Bus.send(PanelService.this, "ping");
            }
        });
        tileByp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bypOn = !bypOn;
                setOn(tileByp, bypOn);
                Bus.send(PanelService.this, "byp " + (bypOn ? 1 : 0));
            }
        });

        LinearLayout r1 = row();
        r1.addView(tileDnd, cell(30, true));
        r1.addView(tRam, cell(30, true));
        r1.addView(tShot, cell(30, false));
        LinearLayout r2 = row();
        r2.addView(tileRec, cell(30, true));
        r2.addView(tilePing, cell(30, true));
        r2.addView(tileByp, cell(30, false));
        card.addView(r1, fullRow(7));
        card.addView(r2, fullRow(5));

        // footer: status message + settings toggle
        LinearLayout foot = row();
        foot.setGravity(Gravity.CENTER_VERTICAL);
        tvMsg = tv("Waiting for Frameless_AI\u2026", 11, MUT, false);
        tvMsg.setSingleLine(true);
        tvMsg.setEllipsize(TextUtils.TruncateAt.END);
        foot.addView(tvMsg, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView gear = tv("\u2699", 17, MUT, false);
        gear.setPadding(dp(10), dp(2), dp(2), dp(2));
        gear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                settingsShown = !settingsShown;
                settingsRow.setVisibility(settingsShown ? View.VISIBLE : View.GONE);
                fitPanel();
            }
        });
        foot.addView(gear);
        card.addView(foot, fullRow(8));

        // panel settings: side / size / opacity (saved, applied instantly)
        settingsRow = row();
        settingsRow.addView(settingChip(Prefs.sideLabel(this), 0), ....
        settingsRow.addView(settingChip(Pref
