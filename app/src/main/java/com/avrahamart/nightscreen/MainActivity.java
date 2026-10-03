package com.avrahamart.nightscreen;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.Gravity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.RectF;
import android.content.Context;
import android.content.res.Configuration;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private NightView nightView;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (nightView != null) nightView.tick();
            handler.postDelayed(this, 250L);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUi();
        nightView = new NightView(this);
        setContentView(nightView);
    }

    @Override protected void onResume() {
        super.onResume();
        hideSystemUi();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    private void hideSystemUi() {
        Window w = getWindow();
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            w.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    private static class NightView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thin = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF player = new RectF();
        private final Calendar now = Calendar.getInstance();
        private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        private final SimpleDateFormat gregFmt = new SimpleDateFormat("EEEE · d MMMM yyyy", Locale.getDefault());
        private String time = "";
        private String gregorian = "";
        private String hebrew = "";
        private boolean playing = false;
        private int minutePulse = 0;

        NightView(Context context) {
            super(context);
            setBackgroundColor(0xFF000000);
            p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            thin.setStrokeWidth(dp(1));
            setFocusable(true);
        }

        void tick() {
            now.setTimeInMillis(System.currentTimeMillis());
            time = timeFmt.format(now.getTime());
            gregorian = gregFmt.format(now.getTime());
            hebrew = HebrewCalendar.formatToday(now);
            minutePulse = now.get(Calendar.SECOND);
            invalidate();
        }

        private float dp(float v) {
            return v * getResources().getDisplayMetrics().density;
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth(), h = getHeight();
            float scale = Math.min(w, h) / 1080f;
            c.save();
            c.scale(scale, scale);
            float sw = w / scale, sh = h / scale;

            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(0xFFF7F7F7);
            p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            p.setTextSize(184);
            c.drawText(time, sw / 2f, sh * 0.43f, p);

            p.setColor(0xFFB8B8B8);
            p.setTextSize(34);
            c.drawText(gregorian, sw / 2f, sh * 0.50f, p);

            p.setColor(0xFFEAEAEA);
            p.setTextSize(40);
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            c.drawText(hebrew, sw / 2f, sh * 0.56f, p);

            p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            p.setTextSize(18);
            p.setColor(0xFF6D6D6D);
            c.drawText("NIGHT SCREEN", sw / 2f, sh * 0.11f, p);

            // Minimal player
            float pw = Math.min(760, sw * 0.78f);
            float ph = 88;
            float px = (sw - pw) / 2f;
            float py = sh - 145;
            player.set(px, py, px + pw, py + ph);
            p.setColor(0xFF111111);
            c.drawRoundRect(player, 44, 44, p);
            thin.setColor(0xFF292929);
            thin.setStyle(Paint.Style.STROKE);
            thin.setStrokeWidth(1.5f);
            c.drawRoundRect(player, 44, 44, thin);
            thin.setStyle(Paint.Style.FILL);

            p.setTextAlign(Paint.Align.LEFT);
            p.setColor(0xFFE7E7E7);
            p.setTextSize(22);
            c.drawText("נגן", px + 28, py + 35, p);
            p.setColor(0xFF777777);
            p.setTextSize(16);
            c.drawText(playing ? "מנגן עכשיו" : "מושהה", px + 28, py + 61, p);

            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(0xFFF3F3F3);
            float cx = px + pw - 132;
            c.drawCircle(cx, py + ph/2f, 25, p);
            p.setColor(0xFF000000);
            if (playing) {
                c.drawRect(cx - 6, py + 37, cx - 1, py + 51, p);
                c.drawRect(cx + 2, py + 37, cx + 7, py + 51, p);
            } else {
                android.graphics.Path tri = new android.graphics.Path();
                tri.moveTo(cx - 5, py + 34);
                tri.lineTo(cx + 8, py + 44);
                tri.lineTo(cx - 5, py + 54);
                tri.close();
                c.drawPath(tri, p);
            }

            p.setColor(0xFF4D4D4D);
            p.setTextSize(20);
            c.drawText("‹", px + pw - 82, py + 52, p);
            c.drawText("›", px + pw - 38, py + 52, p);

            p.setTextSize(15);
            p.setColor(0xFF5A5A5A);
            c.drawText(String.format(Locale.US, "%02d:%02d", minutePulse / 60, minutePulse % 60),
                sw/2f, py - 24, p);

            c.restore();
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent e) {
            if (e.getAction() == android.view.MotionEvent.ACTION_UP) {
                float scale = Math.min(getWidth(), getHeight()) / 1080f;
                float x = e.getX() / scale, y = e.getY() / scale;
                float sh = getHeight() / scale;
                float sw = getWidth() / scale;
                float pw = Math.min(760, sw * 0.78f);
                float ph = 88;
                float px = (sw - pw) / 2f;
                float py = sh - 145;
                if (y >= py && y <= py + ph && x >= px + pw - 160) {
                    playing = !playing;
                    invalidate();
                    return true;
                }
                hide();
            }
            return true;
        }

        private void hide() {
            setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }
}
