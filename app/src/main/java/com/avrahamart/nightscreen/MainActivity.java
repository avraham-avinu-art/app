package com.avrahamart.nightscreen;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Calendar now = Calendar.getInstance();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat gregorianFormat =
            new SimpleDateFormat("EEEE · d MMMM yyyy", Locale.getDefault());

    private TextView timeText;
    private TextView gregorianText;
    private TextView hebrewText;
    private TextView playButton;
    private boolean playing;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateClock();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUi();
        buildUi();
        updateClock();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));

        TextView brand = textView("NIGHT SCREEN", 14, Color.rgb(105,105,105));
        brand.setGravity(Gravity.CENTER);
        root.addView(brand, matchWrap());

        timeText = textView("", 82, Color.rgb(247,247,247));
        timeText.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        timeText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams timeParams = matchWrap();
        timeParams.topMargin = dp(72);
        root.addView(timeText, timeParams);

        gregorianText = textView("", 20, Color.rgb(184,184,184));
        gregorianText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams gregParams = matchWrap();
        gregParams.topMargin = dp(8);
        root.addView(gregorianText, gregParams);

        hebrewText = textView("", 25, Color.rgb(234,234,234));
        hebrewText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        hebrewText.setGravity(Gravity.CENTER);
        hebrewText.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        LinearLayout.LayoutParams hebParams = matchWrap();
        hebParams.topMargin = dp(10);
        root.addView(hebrewText, hebParams);

        SpaceSpacer spacer = new SpaceSpacer(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout player = new LinearLayout(this);
        player.setOrientation(LinearLayout.HORIZONTAL);
        player.setGravity(Gravity.CENTER_VERTICAL);
        player.setPadding(dp(24), 0, dp(16), 0);
        player.setBackgroundColor(Color.rgb(17,17,17));

        TextView label = textView("נגן", 20, Color.rgb(231,231,231));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, dp(72), 1f);
        player.addView(label, labelParams);

        playButton = new TextView(this);
        playButton.setGravity(Gravity.CENTER);
        playButton.setTextSize(24);
        playButton.setTextColor(Color.BLACK);
        playButton.setBackgroundColor(Color.rgb(243,243,243));
        playButton.setText("▶");
        playButton.setOnClickListener(v -> {
            playing = !playing;
            playButton.setText(playing ? "Ⅱ" : "▶");
        });
        player.addView(playButton, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout.LayoutParams playerParams =
                new LinearLayout.LayoutParams(dp(760), dp(72));
        playerParams.width = Math.min(dp(760), getResources().getDisplayMetrics().widthPixels - dp(48));
        playerParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(player, playerParams);

        setContentView(root);
    }

    private void updateClock() {
        now.setTimeInMillis(System.currentTimeMillis());
        timeText.setText(timeFormat.format(now.getTime()));
        gregorianText.setText(gregorianFormat.format(now.getTime()));

        try {
            hebrewText.setText(JewishDateSource.today());
        } catch (Throwable t) {
            hebrewText.setText("");
        }
    }

    private TextView textView(String text, float size, int color) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        return v;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void hideSystemUi() {
        Window w = getWindow();
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        } else {
            w.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    private static final class SpaceSpacer extends View {
        SpaceSpacer(android.content.Context context) {
            super(context);
        }
    }
}
