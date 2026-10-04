package com.avrahamart.nightscreen;

import android.app.Activity;
import android.animation.ObjectAnimator;
import android.app.NotificationManager;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.provider.Settings;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter;
import com.kosherjava.zmanim.hebrewcalendar.JewishDate;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoOffRunnable = () -> {
        try { finishAndRemoveTask(); } catch (Throwable ignored) { finish(); }
    };
    private final Calendar now = Calendar.getInstance();
    private final SimpleDateFormat numericDateFormat =
            new SimpleDateFormat("dd.MM.yyyy", Locale.US);

    private TextView timeText;
    private TextView gregorianText;
    private TextView hebrewText;
    private final int dateTextColor = Color.rgb(220, 220, 220);
    private SharedPreferences prefs;
    private TextView settingsButton;
    private LinearLayout currentSettingsCard;
    private FrameLayout stageView;
    private LinearLayout clockBlockView;
    private LinearLayout mediaHolderView;
    private TextView mediaDetails;
    private TextView mediaProgressText;
    private TextView mediaCurrentText;
    private TextView mediaTotalText;
    private LinearLayout mediaProgressRow;
    private SeekBar mediaSeekBar;
    private MediaController lastKnownMediaController;
    private ObjectAnimator titleMarqueeAnimator;
    private String lastTitle = "";
    private String lastArtist = "";

    private LinearLayout mediaSetup;
    private LinearLayout mediaMirror;
    private TextView mediaTitle;
    private ImageView mediaPlayPause;
    private ImageView mediaPrev;
    private ImageView mediaNext;

    private MediaSessionManager mediaSessionManager;
    private MediaController mediaController;
    private MediaController.Callback mediaCallback;
    private MediaSessionManager.OnActiveSessionsChangedListener activeSessionsListener;
    private boolean activeSessionsListenerRegistered = false;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateClock();
            refreshSystemMediaMirror();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            prefs = getSharedPreferences("settings", MODE_PRIVATE);
            initSettings();
            getWindow().setFlags(
                    android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().addFlags(
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            hideSystemUi();
            buildUi();
            applySettings();
            updateClock();
            scheduleAutoOff();
        } catch (Throwable t) {
            showFatalError(t);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            hideSystemUi();
            handler.removeCallbacks(ticker);
            handler.post(ticker);
            registerActiveSessionsListener();
            handler.postDelayed(this::refreshSystemMediaMirror, 250L);
        } catch (Throwable t) {
            showFatalError(t);
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        unregisterActiveSessionsListener();
        detachMediaController();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(autoOffRunnable);
        unregisterActiveSessionsListener();
        stopTitleMarquee();
        detachMediaController();
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(10), 0, dp(10), dp(4));

        FrameLayout stage = new FrameLayout(this);
        stageView = stage;
        stage.setBackgroundColor(Color.BLACK);
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1f));

        settingsButton = textView("⚙", 20, Color.WHITE);
        settingsButton.setAlpha(0.20f);
        settingsButton.setGravity(Gravity.CENTER);
        settingsButton.setContentDescription("הגדרות");
        settingsButton.setOnClickListener(v -> showSettings());
        FrameLayout.LayoutParams settingsParams = new FrameLayout.LayoutParams(
                dp(30), dp(34), Gravity.TOP | Gravity.LEFT);
        settingsParams.leftMargin = dp(2);
        settingsParams.topMargin = 0;
        stage.addView(settingsButton, settingsParams);

        LinearLayout clockBlock = new LinearLayout(this);
        clockBlockView = clockBlock;
        clockBlock.setOrientation(LinearLayout.VERTICAL);
        clockBlock.setGravity(Gravity.CENTER_HORIZONTAL);

        timeText = textView("--:--", getInt("clockDateSize", 72), Color.rgb(247, 247, 247));
        timeText.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        timeText.setGravity(Gravity.CENTER);
        timeText.setIncludeFontPadding(true);
        int initialClockSize = getInt("clockDateSize", 72);
        int initialClockHeight = Math.max(dp(62), dp(initialClockSize + 20));
        clockBlock.addView(timeText, new LinearLayout.LayoutParams(-1, initialClockHeight));

        gregorianText = textView("", 22, dateTextColor);
        gregorianText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        gregorianText.setGravity(Gravity.CENTER);
        gregorianText.setIncludeFontPadding(true);
        int initialDateHeight = Math.max(dp(24), dp(Math.round(initialClockSize * 0.45f)));
        clockBlock.addView(gregorianText, new LinearLayout.LayoutParams(-1, initialDateHeight));

        hebrewText = textView("", 22, dateTextColor);
        hebrewText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        hebrewText.setGravity(Gravity.CENTER);
        hebrewText.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        hebrewText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        hebrewText.setIncludeFontPadding(true);
        clockBlock.addView(hebrewText, new LinearLayout.LayoutParams(-1, initialDateHeight));

        int initialClockBlockHeight = initialClockHeight + initialDateHeight * 2;
        stage.addView(clockBlock, new FrameLayout.LayoutParams(-1, initialClockBlockHeight, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        LinearLayout mediaHolder = new LinearLayout(this);
        mediaHolderView = mediaHolder;
        mediaHolder.setOrientation(LinearLayout.VERTICAL);
        mediaHolder.setGravity(Gravity.CENTER_HORIZONTAL);
        stage.addView(mediaHolder, new FrameLayout.LayoutParams(-1, -2));

        buildMediaSetup(mediaHolder);
        buildSystemMediaMirror(mediaHolder);

        stage.post(() -> {
            applySettings();
            updateScreenPositions(stageView, clockBlockView, mediaHolderView);
        });
        setContentView(root);
    }

    private void updateScreenPositions(FrameLayout stage, View clockBlock, View mediaHolder) {
        if (stage == null) return;
        try {
            int h = stage.getHeight();
            if (h <= 0) return;

            String clockPos = getString("clockDatePosition", "top");
            String playerPos = getString("playerPosition", "bottom");

            FrameLayout.LayoutParams cp = (FrameLayout.LayoutParams) clockBlock.getLayoutParams();
            int clockH = clockBlock.getMeasuredHeight();
            if ("upper".equals(clockPos)) {
                cp.topMargin = Math.max(0, Math.round(h * 0.25f - clockH / 2f));
            } else if ("middle".equals(clockPos)) {
                cp.topMargin = Math.max(0, Math.round(h * 0.50f - clockH / 2f));
            } else {
                cp.topMargin = 0;
            }
            clockBlock.setLayoutParams(cp);

            FrameLayout.LayoutParams mp = (FrameLayout.LayoutParams) mediaHolder.getLayoutParams();
            if ("bottom".equals(playerPos)) {
                // Let FrameLayout anchor the entire holder to the real bottom.
                // This avoids the first-layout race where measured height is still stale
                // and the player appears too low/cut off until the Activity is reopened.
                mp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                mp.topMargin = 0;
                mp.bottomMargin = 0;
                mp.height = FrameLayout.LayoutParams.WRAP_CONTENT;
            } else {
                mp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                int mediaH = Math.max(0, mediaHolder.getMeasuredHeight());
                mp.topMargin = Math.max(0, Math.round((h * 2f / 3f) - mediaH));
                mp.bottomMargin = 0;
                mp.height = FrameLayout.LayoutParams.WRAP_CONTENT;
            }
            mediaHolder.setLayoutParams(mp);
        } catch (Throwable ignored) {}
    }

    private void buildMediaSetup(LinearLayout root) {
        mediaSetup = new LinearLayout(this);
        mediaSetup.setOrientation(LinearLayout.HORIZONTAL);
        mediaSetup.setGravity(Gravity.CENTER_VERTICAL);
        mediaSetup.setPadding(dp(8), dp(5), dp(8), dp(5));
        mediaSetup.setBackgroundColor(Color.BLACK);
        mediaSetup.setVisibility(View.GONE);

        TextView message = textView(
                "יש לאפשר גישה להתראות כדי להציג את נגן המערכת",
                14, Color.rgb(205, 205, 205));
        message.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        message.setTextDirection(View.TEXT_DIRECTION_RTL);
        message.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        message.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        message.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        message.setSingleLine(false);
        message.setMaxLines(2);
        message.setEllipsize(null);
        message.setIncludeFontPadding(false);

        LinearLayout.LayoutParams messageParams =
                new LinearLayout.LayoutParams(0, dp(58), 1f);
        messageParams.leftMargin = dp(6);
        mediaSetup.addView(message, messageParams);

        TextView openSettings = mediaButton("הפעל");
        openSettings.setTextSize(14);
        openSettings.setTextColor(Color.WHITE);
        openSettings.setBackgroundColor(Color.rgb(48, 48, 48));
        openSettings.setGravity(Gravity.CENTER);
        openSettings.setOnClickListener(v -> openNotificationAccessSettings());
        mediaSetup.addView(openSettings, new LinearLayout.LayoutParams(dp(76), dp(46)));

        int availableWidth = Math.max(
                dp(1),
                getResources().getDisplayMetrics().widthPixels - dp(20));
        LinearLayout.LayoutParams setupParams = new LinearLayout.LayoutParams(
                Math.min(dp(760), availableWidth), dp(68));
        setupParams.gravity = Gravity.CENTER_HORIZONTAL;
        setupParams.bottomMargin = dp(5);
        root.addView(mediaSetup, setupParams);
    }

    private void buildSystemMediaMirror(LinearLayout root) {
        mediaMirror = new LinearLayout(this);
        mediaMirror.setOrientation(LinearLayout.VERTICAL);
        mediaMirror.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        mediaMirror.setPadding(0, 0, 0, 0);
        mediaMirror.setBackgroundColor(Color.BLACK);
        mediaMirror.setVisibility(View.GONE);

        // The reference player is intentionally clean: the position row is
        // the first visible element and the three controls are widely spaced.
        mediaTitle = textView("", 17, Color.WHITE);
        mediaTitle.setVisibility(View.VISIBLE);
        mediaTitle.setGravity(Gravity.CENTER);
        mediaTitle.setSingleLine(true);
        mediaTitle.setIncludeFontPadding(true);
        mediaDetails = textView("", 17, Color.rgb(175, 175, 175));
        mediaDetails.setVisibility(View.INVISIBLE);
        mediaDetails.setGravity(Gravity.CENTER);
        mediaDetails.setSingleLine(true);
        mediaDetails.setIncludeFontPadding(true);
        mediaProgressText = textView("", 12, Color.WHITE);
        mediaProgressText.setVisibility(View.GONE);

        mediaProgressRow = new LinearLayout(this);
        mediaProgressRow.setOrientation(LinearLayout.HORIZONTAL);
        mediaProgressRow.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        mediaProgressRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        mediaProgressRow.setPadding(dp(4), 0, dp(4), 0);
        mediaProgressRow.setVisibility(View.GONE);

        mediaCurrentText = textView("0:00", 12, Color.WHITE);
        mediaCurrentText.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        mediaCurrentText.setSingleLine(true);
        mediaCurrentText.setIncludeFontPadding(false);
        LinearLayout.LayoutParams currentParams =
                new LinearLayout.LayoutParams(dp(48), dp(32));
        currentParams.rightMargin = dp(4);
        mediaProgressRow.addView(mediaCurrentText, currentParams);

        mediaSeekBar = new SeekBar(this);
        mediaSeekBar.setVisibility(View.GONE);
        mediaSeekBar.setPadding(dp(7), 0, dp(7), 0);
        mediaSeekBar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        mediaSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaController != null) {
                    PlaybackState st = mediaController.getPlaybackState();
                    if (st != null && mediaController.getMetadata() != null) {
                        long duration = mediaController.getMetadata()
                                .getLong(MediaMetadata.METADATA_KEY_DURATION);
                        if (duration > 0) {
                            mediaController.getTransportControls().seekTo(
                                    (duration * progress) / 1000L);
                        }
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // The user requested the progress line to be 50% shorter than the previous version.
        int lineWidth = Math.max(
                dp(120),
                Math.round(getResources().getDisplayMetrics().widthPixels * 0.36f));
        mediaProgressRow.addView(mediaSeekBar,
                new LinearLayout.LayoutParams(lineWidth, dp(32)));

        mediaTotalText = textView("0:00", 12, Color.WHITE);
        mediaTotalText.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        mediaTotalText.setSingleLine(true);
        mediaTotalText.setIncludeFontPadding(false);
        LinearLayout.LayoutParams totalParams =
                new LinearLayout.LayoutParams(dp(48), dp(32));
        totalParams.leftMargin = dp(4);
        mediaProgressRow.addView(mediaTotalText, totalParams);

        // Artist first, then song title, followed immediately by the position row.
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(-1, dp(30));
        mediaMirror.addView(mediaDetails, detailsParams);

        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, dp(30));
        titleParams.topMargin = dp(0);
        mediaMirror.addView(mediaTitle, titleParams);

        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(28));
        progressParams.topMargin = dp(0);
        mediaMirror.addView(mediaProgressRow, progressParams);

        FrameLayout controls = new FrameLayout(this);
        controls.setPadding(0, 0, 0, 0);
        controls.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        int buttonSize = dp(44);
        int controlWidth = Math.max(
                dp(180),
                Math.round(getResources().getDisplayMetrics().widthPixels * 0.44f));
        LinearLayout.LayoutParams controlAreaParams =
                new LinearLayout.LayoutParams(controlWidth, dp(44));
        controlAreaParams.gravity = Gravity.CENTER_HORIZONTAL;
        controlAreaParams.topMargin = dp(-5);

        // Physical order: Next on the left, Play/Pause in the center, Previous on the right.
        mediaNext = mediaIconButton(R.drawable.media_next, "הבא");
        mediaNext.setOnClickListener(v -> sendPrevious());
        FrameLayout.LayoutParams nextParams =
                new FrameLayout.LayoutParams(buttonSize, buttonSize, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        controls.addView(mediaNext, nextParams);

        mediaPlayPause = mediaIconButton(R.drawable.media_play, "נגן");
        mediaPlayPause.setOnClickListener(v -> sendPlayPause());
        FrameLayout.LayoutParams playParams =
                new FrameLayout.LayoutParams(buttonSize, buttonSize, Gravity.CENTER);
        controls.addView(mediaPlayPause, playParams);

        mediaPrev = mediaIconButton(R.drawable.media_previous, "הקודם");
        mediaPrev.setOnClickListener(v -> sendNext());
        FrameLayout.LayoutParams prevParams =
                new FrameLayout.LayoutParams(buttonSize, buttonSize, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        controls.addView(mediaPrev, prevParams);

        mediaMirror.addView(controls, controlAreaParams);

        LinearLayout.LayoutParams mirrorParams =
                new LinearLayout.LayoutParams(
                        Math.min(dp(760), Math.max(dp(1),
                                getResources().getDisplayMetrics().widthPixels - dp(20))),
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        mirrorParams.gravity = Gravity.CENTER_HORIZONTAL;
        mirrorParams.topMargin = dp(28);
        mirrorParams.bottomMargin = 0;
        root.addView(mediaMirror, mirrorParams);
    }

    private void initSettings() {
        if (prefs == null) {
            prefs = getSharedPreferences("settings", MODE_PRIVATE);
        }

        SharedPreferences.Editor e = prefs.edit();

        e.putInt("fontColor", getInt("fontColor", dateTextColor));
        e.putInt("fontSize", getInt("fontSize", 17));
        e.putInt("clockDateSize", getInt("clockDateSize", 72));
        e.putInt("playerSize", getInt("playerSize", 17));
        e.putInt("fontWeight", getInt("fontWeight", 1));
        e.putString("fontFamily", getString("fontFamily", "sans-serif"));

        e.putBoolean("showGregorian", getBool("showGregorian", true));
        e.putBoolean("showHebrew", getBool("showHebrew", true));
        e.putBoolean("showPlayer", getBool("showPlayer", true));
        e.putBoolean("showDetails", getBool("showDetails", true));
        e.putBoolean("showProgress", getBool("showProgress", true));
        lastTitle = getString("lastTitle", lastTitle);
        lastArtist = getString("lastArtist", lastArtist);

        if (!getBool("referenceLayoutV3Applied", false)) {
            e.putBoolean("showDetails", getBool("showDetails", true));
            e.putBoolean("showProgress", true);
            e.putBoolean("frameEnabled", false);
            e.putBoolean("referenceLayoutV3Applied", true);
        } else if (!getBool("referenceLayoutV2Applied", false)) {
            e.putBoolean("frameEnabled", false);
            e.putBoolean("referenceLayoutV2Applied", true);
        } else {
            e.putBoolean("frameEnabled", getBool("frameEnabled", false));
        }
        e.putInt("frameColor", getInt("frameColor", Color.WHITE));
        e.putInt("frameWidth", getInt("frameWidth", 1));
        e.putInt("frameRadius", getInt("frameRadius", 10));
        e.putInt("buttonColor", getInt("buttonColor", Color.rgb(45,45,45)));
        e.putInt("buttonBorderColor", getInt("buttonBorderColor", Color.rgb(110,110,110)));
        e.putString("screenSaverDuration", getString("screenSaverDuration", "0"));
        if (!getBool("defaultLayoutV4Applied", false)) {
            // Apply the requested defaults once to existing installations too.
            // Do not overwrite these pending editor values with the old preference values.
            e.putInt("clockDateSize", 72);
            e.putString("clockDatePosition", "upper");
            e.putString("playerPosition", "bottom");
            e.putBoolean("defaultLayoutV4Applied", true);
        } else {
            e.putString("clockDatePosition", getString("clockDatePosition", "upper"));
            e.putString("playerPosition", getString("playerPosition", "bottom"));
        }
        e.commit();
    }

    private boolean getBool(String key, boolean def) {
        if (prefs == null) return def;
        try {
            Object value = prefs.getAll().get(key);
            if (value instanceof Boolean) return (Boolean) value;
            if (value instanceof String) return Boolean.parseBoolean((String) value);
            if (value instanceof Number) return ((Number) value).intValue() != 0;
        } catch (Throwable ignored) {
        }
        return def;
    }

    private int getInt(String key, int def) {
        if (prefs == null) return def;
        try {
            Object value = prefs.getAll().get(key);
            if (value instanceof Number) return ((Number) value).intValue();
            if (value instanceof String) return Integer.parseInt((String) value);
            if (value instanceof Boolean) return ((Boolean) value) ? 1 : 0;
        } catch (Throwable ignored) {
        }
        return def;
    }

    private String getString(String key, String def) {
        if (prefs == null) return def;
        try {
            Object value = prefs.getAll().get(key);
            return value == null ? def : String.valueOf(value);
        } catch (Throwable ignored) {
            return def;
        }
    }

    private int blendWithBlack(int color, float factor) {
        float f = Math.max(0f, Math.min(1f, factor));
        return Color.rgb(
                Math.round(Color.red(color) * f),
                Math.round(Color.green(color) * f),
                Math.round(Color.blue(color) * f));
    }

    private String formatMs(long ms) {
        long t = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%d:%02d", (t / 60) % 60, t % 60);
    }

    private void applySettings() {
        if (prefs == null || gregorianText == null) return;

        int color = getInt("fontColor", dateTextColor);
        int size = getInt("fontSize", 17);
        int clockDateSize = getInt("clockDateSize", 72);
        int playerSize = getInt("playerSize", 17);
        int weight = getInt("fontWeight", 1);
        Typeface tf = Typeface.create(
                getString("fontFamily", "sans-serif"),
                weight == 2 ? Typeface.BOLD : Typeface.NORMAL);

        gregorianText.setVisibility(
                getBool("showGregorian", true) ? View.VISIBLE : View.GONE);
        hebrewText.setVisibility(
                getBool("showHebrew", true) ? View.VISIBLE : View.GONE);

        gregorianText.setTextColor(color);
        hebrewText.setTextColor(color);
        timeText.setTextColor(color);
        gregorianText.setTextSize(Math.max(14, clockDateSize * 0.30f));
        hebrewText.setTextSize(Math.max(14, clockDateSize * 0.30f));
        timeText.setTextSize(clockDateSize);
        gregorianText.setTypeface(tf);
        hebrewText.setTypeface(tf);
        timeText.setTypeface(tf);

        View clockParent = (View) timeText.getParent();
        if (clockParent instanceof LinearLayout) {
            LinearLayout cb = (LinearLayout) clockParent;
            int clockH = Math.max(dp(62), dp(clockDateSize + 20));
            int dateH = Math.max(dp(24), dp(Math.round(clockDateSize * 0.45f)));
            LinearLayout.LayoutParams tp = (LinearLayout.LayoutParams) timeText.getLayoutParams();
            tp.height = clockH;
            timeText.setLayoutParams(tp);
            LinearLayout.LayoutParams gp = (LinearLayout.LayoutParams) gregorianText.getLayoutParams();
            gp.height = dateH;
            gregorianText.setLayoutParams(gp);
            LinearLayout.LayoutParams hp = (LinearLayout.LayoutParams) hebrewText.getLayoutParams();
            hp.height = dateH;
            hebrewText.setLayoutParams(hp);
            FrameLayout.LayoutParams cp = (FrameLayout.LayoutParams) cb.getLayoutParams();
            cp.height = clockH + dateH * 2;
            cb.setLayoutParams(cp);
        }

        if (mediaTitle != null) {
            mediaTitle.setTypeface(tf);
            mediaDetails.setTypeface(tf);
            mediaProgressText.setTypeface(tf);
            mediaTitle.setTextSize(playerSize);
            mediaDetails.setTextSize(playerSize);
            int textRowHeight = Math.max(dp(30), dp(playerSize + 12));
            if (mediaTitle.getLayoutParams() != null) {
                mediaTitle.getLayoutParams().height = textRowHeight;
                mediaTitle.requestLayout();
            }
            if (mediaDetails.getLayoutParams() != null) {
                mediaDetails.getLayoutParams().height = textRowHeight;
                mediaDetails.requestLayout();
            }
            mediaProgressText.setTextSize(Math.max(10, playerSize - 4));

            // Scale the control buttons together with the selected player size.
            float scale = Math.max(0.72f, Math.min(1.45f, playerSize / 17f));
            int scaledButtonSize = Math.round(dp(44) * scale);
            if (mediaPrev != null && mediaPlayPause != null && mediaNext != null) {
                mediaPrev.getLayoutParams().width = scaledButtonSize;
                mediaPrev.getLayoutParams().height = scaledButtonSize;
                mediaPlayPause.getLayoutParams().width = scaledButtonSize;
                mediaPlayPause.getLayoutParams().height = scaledButtonSize;
                mediaNext.getLayoutParams().width = scaledButtonSize;
                mediaNext.getLayoutParams().height = scaledButtonSize;
                mediaPrev.requestLayout();
                mediaPlayPause.requestLayout();
                mediaNext.setScaleType(ImageView.ScaleType.FIT_CENTER);
                mediaPlayPause.setScaleType(ImageView.ScaleType.FIT_CENTER);
                mediaPrev.setScaleType(ImageView.ScaleType.FIT_CENTER);
                int iconPadding = Math.max(dp(2), Math.round(dp(6) / scale));
                mediaNext.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
                mediaPlayPause.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
                mediaPrev.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
                mediaNext.requestLayout();
                View controls = (View) mediaPrev.getParent();
                if (controls != null) {
                    LinearLayout.LayoutParams cp = (LinearLayout.LayoutParams) controls.getLayoutParams();
                    cp.height = scaledButtonSize;
                    controls.setLayoutParams(cp);
                }
            }

            mediaTitle.setTextColor(color);
            mediaDetails.setTextColor(color);
            mediaProgressText.setTextColor(color);
            mediaCurrentText.setTextColor(color);
            mediaTotalText.setTextColor(color);
            mediaSeekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
            mediaSeekBar.setProgressBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(blendWithBlack(color, 0.48f)));
            mediaSeekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(color));

            boolean hasActiveSong = mediaController != null &&
                    mediaController.getMetadata() != null &&
                    mediaController.getMetadata().getString(MediaMetadata.METADATA_KEY_TITLE) != null &&
                    !mediaController.getMetadata().getString(MediaMetadata.METADATA_KEY_TITLE).trim().isEmpty();
            if (getBool("frameEnabled", false) && hasActiveSong) {
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(Color.BLACK);
                bg.setCornerRadius(dp(getInt("frameRadius", 10)));
                bg.setStroke(
                        Math.max(0, dp(getInt("frameWidth", 1))),
                        getInt("frameColor", Color.WHITE));
                mediaMirror.setBackground(bg);
            } else {
                mediaMirror.setBackgroundColor(Color.BLACK);
            }

            mediaNext.setBackgroundColor(Color.TRANSPARENT);
            mediaPrev.setBackgroundColor(Color.TRANSPARENT);
            mediaPlayPause.setBackgroundColor(Color.TRANSPARENT);
            if (mediaNext.getDrawable() != null) mediaNext.getDrawable().setTint(color);
            if (mediaPrev.getDrawable() != null) mediaPrev.getDrawable().setTint(color);
            if (mediaPlayPause.getDrawable() != null) mediaPlayPause.getDrawable().setTint(color);
            View parent = mediaMirror.getParent() instanceof View ? (View) mediaMirror.getParent() : null;
            if (parent != null && parent.getParent() instanceof FrameLayout) {
                FrameLayout stage = (FrameLayout) parent.getParent();
                updateScreenPositions(stageView, clockBlockView, mediaHolderView);
            }
            scheduleAutoOff();
        }

    }

    private void scheduleAutoOff() {
        handler.removeCallbacks(autoOffRunnable);
        String value = getString("screenSaverDuration", "0");
        long minutes = 0L;
        try { minutes = Long.parseLong(value); } catch (Throwable ignored) {}
        if (minutes > 0L) handler.postDelayed(autoOffRunnable, minutes * 60_000L);
    }

    private void showSettings() {
        try {
            if (prefs == null) initSettings();

            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER_HORIZONTAL);
            box.setPadding(dp(12), dp(4), dp(12), dp(8));
            box.setBackgroundColor(Color.rgb(248, 249, 251));
            scroll.addView(box);

            TextView h = textView("הגדרות", 22, Color.rgb(25, 28, 35));
            h.setGravity(Gravity.CENTER);
            h.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            box.addView(h, new LinearLayout.LayoutParams(-1, dp(34)));

            addSection(box, "תצוגת שעון ותאריך");
            addSpinner(box, "גודל שעה ותאריך",
                    new String[]{"קטן","בינוני","גדול","גדול מאוד"},
                    new String[]{"38","48","60","72"}, "clockDateSize");
            addSpinner(box, "מיקום שעה ותאריך",
                    new String[]{"הכי למעלה","ברבע העליון","באמצע המסך"},
                    new String[]{"top","upper","middle"}, "clockDatePosition");
            addSpinner(box, "זמן פעילות השומר מסך",
                    new String[]{"ללא כיבוי","1 דקות","5 דקות"},
                    new String[]{"0","1","5"}, "screenSaverDuration");

            addSection(box, "גופן");
            addSpinner(box, "צבע",
                    new String[]{"לבן","אפור בהיר","אפור","כחול עדין"},
                    new String[]{"white","light","gray","blue"}, "fontColor");
            addSpinner(box, "עובי",
                    new String[]{"רגיל","בינוני","מודגש"},
                    new String[]{"0","1","2"}, "fontWeight");
            addSpinner(box, "סוג",
                    new String[]{"Sans","Monospace","Serif"},
                    new String[]{"sans-serif","monospace","serif"}, "fontFamily");

            addSection(box, "תצוגה");
            addCheck(box, "תאריך לועזי", "showGregorian", true);
            addCheck(box, "תאריך עברי", "showHebrew", true);
            addCheck(box, "נגן", "showPlayer", true);

            addSection(box, "נגן");
            addSpinner(box, "גודל נגן",
                    new String[]{"קטן","בינוני","גדול","גדול מאוד"},
                    new String[]{"14","17","20","23"}, "playerSize");
            addSpinner(box, "מיקום הנגן",
                    new String[]{"בשליש התחתון","הכי למטה"},
                    new String[]{"lower","bottom"}, "playerPosition");
            addCheck(box, "פרטי אמן (כשקיים בלבד)", "showDetails", getBool("showDetails", true));
            addCheck(box, "שורת מיקום + זמן", "showProgress", true);

            addSection(box, "מסגרת נגן");
            addCheck(box, "הצג מסגרת", "frameEnabled", false);
            addSpinner(box, "צבע מסגרת",
                    new String[]{"לבן","אפור","אפור בהיר","כסף"},
                    new String[]{"white","gray","light","silver"}, "frameColor");
            addSpinner(box, "עובי מסגרת",
                    new String[]{"דקה","בינונית","עבה"},
                    new String[]{"1","2","3"}, "frameWidth");
            addSpinner(box, "עיגול פינות",
                    new String[]{"ישר","עדין","מעוגל"},
                    new String[]{"0","10","20"}, "frameRadius");

            Button done = new Button(this);
            done.setText("סיום");
            done.setTextColor(Color.WHITE);
            done.setTextSize(15);
            GradientDrawable doneBg = new GradientDrawable();
            doneBg.setColor(Color.rgb(55, 95, 145));
            doneBg.setCornerRadius(dp(12));
            done.setBackground(doneBg);
            LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(dp(280), dp(40));
            doneParams.gravity = Gravity.CENTER_HORIZONTAL;
            doneParams.topMargin = dp(5);
            box.addView(done, doneParams);

            AlertDialog dialog = new AlertDialog.Builder(this).setView(scroll).create();
            done.setOnClickListener(v -> dialog.dismiss());
            // Temporarily restore the system bars while settings are open,
            // so the notification shade can be pulled down normally.
            showSystemUiForSettings();

            dialog.setOnDismissListener(d -> {
                applySettings();
                refreshSystemMediaMirror();
                scheduleAutoOff();
                hideSystemUi();
            });

            dialog.show();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.white);
                dialog.getWindow().setDimAmount(0.45f);
                dialog.getWindow().setLayout(
                        Math.min(getResources().getDisplayMetrics().widthPixels - dp(16), dp(600)),
                        Math.min(getResources().getDisplayMetrics().heightPixels - dp(16), dp(820)));
            }
        } catch (Throwable t) {
            showFatalError(t);
        }
    }

    private void addSection(LinearLayout b, String title) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(6), dp(10), dp(6));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Color.rgb(222, 226, 232));
        card.setBackground(bg);
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(2));

        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                -1, LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.bottomMargin = dp(10);
        b.addView(card, cp);

        TextView v = textView(title, 17, Color.rgb(55, 82, 112));
        v.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        v.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        v.setPadding(dp(4), 0, dp(4), 0);
        card.addView(v, new LinearLayout.LayoutParams(-1, dp(34)));
        currentSettingsCard = card;
    }

    private LinearLayout settingsTarget(LinearLayout fallback) {
        return currentSettingsCard != null ? currentSettingsCard : fallback;
    }

    private void addCheck(LinearLayout b, String s, String k, boolean d) {
        SwitchCompat x = new SwitchCompat(this);
        x.setText(s);
        x.setTextColor(Color.rgb(35, 38, 45));
        x.setTextSize(15);
        x.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        x.setPadding(dp(2), 0, dp(2), 0);
        x.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        x.setShowText(false);
        x.setChecked(getBool(k, d));
        x.setOnCheckedChangeListener((a, z) -> {
            try {
                prefs.edit().putBoolean(k, z).commit();
                applySettings();
            } catch (Throwable ignored) {}
        });
        settingsTarget(b).addView(x, new LinearLayout.LayoutParams(-1, dp(42)));
    }

    private void addSpinner(LinearLayout b, String label, String[] names,
                            String[] vals, String key) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView l = textView(label, 16, Color.rgb(45, 48, 55));
        l.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.addView(l, new LinearLayout.LayoutParams(0, dp(34), 1f));

        Spinner sp = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, names) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                v.setTextColor(Color.rgb(35, 38, 45));
                v.setTextSize(14);
                v.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
                v.setPadding(dp(8), 0, dp(8), 0);
                return v;
            }
            @Override public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                TextView v = new TextView(MainActivity.this);
                v.setText(names[position]);
                v.setTextColor(Color.rgb(35, 38, 45));
                v.setTextSize(14);
                v.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
                v.setPadding(dp(10), 0, dp(10), 0);
                v.setBackgroundColor(Color.WHITE);
                v.setMinHeight(dp(32));
                return v;
            }
        };
        sp.setAdapter(adapter);
        GradientDrawable spinnerBg = new GradientDrawable();
        spinnerBg.setColor(Color.WHITE);
        spinnerBg.setCornerRadius(dp(8));
        spinnerBg.setStroke(dp(1), Color.rgb(205, 209, 216));
        sp.setBackground(spinnerBg);

        int ix = 0;
        if (key.equals("fontColor") || key.equals("frameColor")) {
            int stored = getInt(
                    key, key.equals("fontColor") ? dateTextColor : Color.WHITE);

            for (int i = 0; i < vals.length; i++) {
                int col = colorValue(vals[i]);
                if (stored == col) {
                    ix = i;
                    break;
                }
            }
        } else if (key.equals("fontSize") || key.equals("fontWeight")
                || key.equals("clockDateSize") || key.equals("playerSize")
                || key.equals("frameWidth") || key.equals("frameRadius")) {
            int stored = getInt(key, Integer.parseInt(vals[0]));
            for (int i = 0; i < vals.length; i++) {
                try {
                    if (Integer.parseInt(vals[i]) == stored) {
                        ix = i;
                        break;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        } else {
            String cur = getString(key, vals[0]);
            for (int i = 0; i < vals.length; i++) {
                if (vals[i].equals(cur)) {
                    ix = i;
                    break;
                }
            }
        }

        LinearLayout target = settingsTarget(b);
        sp.setSelection(ix, false);
        sp.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(
                            android.widget.AdapterView<?> parent,
                            View view, int pos, long id) {
                        try {
                            String val = vals[pos];
                            SharedPreferences.Editor e = prefs.edit();

                            if (key.equals("fontColor") || key.equals("frameColor")) {
                                e.putInt(key, colorValue(val));
                            } else if (key.equals("fontSize")
                                    || key.equals("fontWeight")
                                    || key.equals("clockDateSize")
                                    || key.equals("playerSize")
                                    || key.equals("frameWidth")
                                    || key.equals("frameRadius")) {
                                e.putInt(key, Integer.parseInt(val));
                            } else {
                                e.putString(key, val);
                            }

                            e.commit();
                            applySettings();
                            handler.post(MainActivity.this::refreshSystemMediaMirror);
                        } catch (Throwable ignored) {
                        }
                    }

                    @Override public void onNothingSelected(
                            android.widget.AdapterView<?> parent) {
                    }
                });

