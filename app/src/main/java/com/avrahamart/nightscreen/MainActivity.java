package com.avrahamart.nightscreen;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
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
import android.provider.Settings;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter;
import com.kosherjava.zmanim.hebrewcalendar.JewishDate;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Calendar now = Calendar.getInstance();
    private final SimpleDateFormat numericDateFormat =
            new SimpleDateFormat("dd-MM-yyyy", Locale.US);

    private TextView timeText;
    private TextView gregorianText;
    private TextView hebrewText;
    private final int dateTextColor = Color.rgb(220, 220, 220);

    private LinearLayout mediaSetup;
    private FrameLayout mediaMirror;
    private ImageView albumArt;
    private TextView mediaTitle;
    private TextView mediaSubtitle;
    private TextView mediaPlayPause;
    private TextView mediaPrev;
    private TextView mediaNext;
    private TextView mediaApp;
    private TextView mediaOutput;
    private MediaProgressView mediaProgress;

    private MediaSessionManager mediaSessionManager;
    private MediaController mediaController;
    private MediaController.Callback mediaCallback;

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
            getWindow().addFlags(
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            hideSystemUi();
            buildUi();
            updateClock();
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
            handler.postDelayed(this::refreshSystemMediaMirror, 250L);
        } catch (Throwable t) {
            showFatalError(t);
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        detachMediaController();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        detachMediaController();
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));

        timeText = textView("--:--", 82, Color.rgb(247, 247, 247));
        timeText.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        timeText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams timeParams = matchWrap();
        timeParams.topMargin = dp(54);
        root.addView(timeText, timeParams);

        gregorianText = textView("", 22, dateTextColor);
        gregorianText.setTypeface(
                Typeface.create("sans-serif-medium", Typeface.NORMAL));
        gregorianText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams gregParams = matchWrap();
        gregParams.topMargin = dp(10);
        root.addView(gregorianText, gregParams);

        hebrewText = textView("", 22, dateTextColor);
        hebrewText.setTypeface(
                Typeface.create("sans-serif-medium", Typeface.NORMAL));
        hebrewText.setGravity(Gravity.CENTER);
        hebrewText.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        hebrewText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        LinearLayout.LayoutParams hebParams = matchWrap();
        hebParams.topMargin = dp(7);
        root.addView(hebrewText, hebParams);

        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        buildMediaSetup(root);
        buildSystemMediaMirror(root);

        setContentView(root);
    }

    private void buildMediaSetup(LinearLayout root) {
        mediaSetup = new LinearLayout(this);
        mediaSetup.setOrientation(LinearLayout.HORIZONTAL);
        mediaSetup.setGravity(Gravity.CENTER_VERTICAL);
        mediaSetup.setPadding(dp(14), dp(8), dp(10), dp(8));
        mediaSetup.setBackgroundColor(Color.rgb(28, 28, 28));
        mediaSetup.setVisibility(View.GONE);

        TextView message = textView(
                "כדי להציג את נגן המערכת יש לאפשר ל-Night Screen גישה להתראות",
                14, Color.rgb(215, 215, 215));
        message.setGravity(Gravity.CENTER_VERTICAL);
        message.setSingleLine(false);
        mediaSetup.addView(message, new LinearLayout.LayoutParams(0, dp(56), 1f));

        TextView openSettings = mediaButton("הפעל גישה");
        openSettings.setTextSize(14);
        openSettings.setTextColor(Color.WHITE);
        openSettings.setBackgroundColor(Color.rgb(58, 58, 58));
        openSettings.setOnClickListener(v -> openNotificationAccessSettings());
        mediaSetup.addView(openSettings, new LinearLayout.LayoutParams(dp(104), dp(46)));

        int availableWidth = Math.max(
                dp(1),
                getResources().getDisplayMetrics().widthPixels - dp(48));
        LinearLayout.LayoutParams setupParams = new LinearLayout.LayoutParams(
                Math.min(dp(760), availableWidth), dp(72));
        setupParams.gravity = Gravity.CENTER_HORIZONTAL;
        setupParams.bottomMargin = dp(8);
        root.addView(mediaSetup, setupParams);
    }

    private void buildSystemMediaMirror(LinearLayout root) {
        mediaMirror = new FrameLayout(this);
        mediaMirror.setVisibility(View.GONE);

        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.rgb(48, 48, 48));
        bg.setCornerRadius(dp(28));
        mediaMirror.setBackground(bg);
        mediaMirror.setClipToOutline(true);
        mediaMirror.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(28));
            }
        });

        albumArt = new ImageView(this);
        albumArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mediaMirror.addView(albumArt, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        View dim = new View(this);
        dim.setBackgroundColor(Color.argb(125, 0, 0, 0));
        mediaMirror.addView(dim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(16), dp(20), dp(14));
        mediaMirror.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        mediaApp = textView("●", 18, Color.WHITE);
        mediaApp.setGravity(Gravity.CENTER);
        top.addView(mediaApp, new LinearLayout.LayoutParams(dp(34), dp(34)));

        View topSpacer = new View(this);
        top.addView(topSpacer, new LinearLayout.LayoutParams(0, 1, 1f));

        mediaOutput = textView("הטלפון הזה", 12, Color.rgb(35, 35, 35));
        mediaOutput.setGravity(Gravity.CENTER);
        mediaOutput.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        android.graphics.drawable.GradientDrawable outputBg =
                new android.graphics.drawable.GradientDrawable();
        outputBg.setColor(Color.argb(225, 245, 245, 245));
        outputBg.setCornerRadius(dp(18));
        mediaOutput.setBackground(outputBg);
        mediaOutput.setPadding(dp(12), 0, dp(12), 0);
        top.addView(mediaOutput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)));

        content.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(38)));

        View titleSpacer = new View(this);
        content.addView(titleSpacer, new LinearLayout.LayoutParams(1, 0, 1f));

        mediaTitle = textView("", 17, Color.WHITE);
        mediaTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mediaTitle.setSingleLine(true);
        content.addView(mediaTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(25)));

        mediaSubtitle = textView("", 14, Color.argb(215, 255, 255, 255));
        mediaSubtitle.setSingleLine(true);
        content.addView(mediaSubtitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(22)));

        mediaProgress = new MediaProgressView(this);
        LinearLayout.LayoutParams progressParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(20));
        progressParams.topMargin = dp(6);
        content.addView(mediaProgress, progressParams);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);

        mediaPrev = mediaButton("◀");
        mediaPrev.setTextSize(22);
        mediaPrev.setTextColor(Color.WHITE);
        mediaPrev.setBackgroundColor(Color.TRANSPARENT);
        mediaPrev.setOnClickListener(v -> sendPrevious());
        controls.addView(mediaPrev, new LinearLayout.LayoutParams(dp(54), dp(48)));

        View cSpacer1 = new View(this);
        controls.addView(cSpacer1, new LinearLayout.LayoutParams(0, 1, 1f));

        mediaPlayPause = mediaButton("Ⅱ");
        mediaPlayPause.setTextSize(25);
        mediaPlayPause.setTextColor(Color.rgb(25, 25, 25));
        android.graphics.drawable.GradientDrawable playBg =
                new android.graphics.drawable.GradientDrawable();
        playBg.setColor(Color.rgb(245, 245, 245));
        playBg.setCornerRadius(dp(22));
        mediaPlayPause.setBackground(playBg);
        mediaPlayPause.setOnClickListener(v -> sendPlayPause());
        controls.addView(mediaPlayPause, new LinearLayout.LayoutParams(dp(58), dp(48)));

        View cSpacer2 = new View(this);
        controls.addView(cSpacer2, new LinearLayout.LayoutParams(0, 1, 1f));

        mediaNext = mediaButton("▶");
        mediaNext.setTextSize(22);
        mediaNext.setTextColor(Color.WHITE);
        mediaNext.setBackgroundColor(Color.TRANSPARENT);
        mediaNext.setOnClickListener(v -> sendNext());
        controls.addView(mediaNext, new LinearLayout.LayoutParams(dp(54), dp(48)));

        content.addView(controls, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50)));

        int availableWidth = Math.max(
                dp(1),
                getResources().getDisplayMetrics().widthPixels - dp(48));
        LinearLayout.LayoutParams mirrorParams =
                new LinearLayout.LayoutParams(
                        Math.min(dp(760), availableWidth), dp(225));
        mirrorParams.gravity = Gravity.CENTER_HORIZONTAL;
        mirrorParams.bottomMargin = dp(6);
        root.addView(mediaMirror, mirrorParams);
    }

    private TextView mediaButton(String symbol) {
        TextView v = textView(symbol, 24, Color.BLACK);
        v.setGravity(Gravity.CENTER);
        v.setClickable(true);
        return v;
    }

    private void updateClock() {
        now.setTimeInMillis(System.currentTimeMillis());
        timeText.setText(numericTime());
        gregorianText.setText(hebrewWeekday() + " · " +
                numericDateFormat.format(now.getTime()));

        try {
            JewishDate jewishDate = new JewishDate(now);
            HebrewDateFormatter formatter = new HebrewDateFormatter();
            formatter.setHebrewFormat(true);
            formatter.setUseGershGershayim(true);
            formatter.setUseLongHebrewYears(false);
            hebrewText.setText(formatter.format(jewishDate));
        } catch (Throwable t) {
            hebrewText.setText("");
        }
    }

    private String numericTime() {
        return String.format(Locale.US, "%02d:%02d",
                now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE));
    }

    private String hebrewWeekday() {
        switch (now.get(Calendar.DAY_OF_WEEK)) {
            case Calendar.SUNDAY: return "יום א";
            case Calendar.MONDAY: return "יום ב";
            case Calendar.TUESDAY: return "יום ג";
            case Calendar.WEDNESDAY: return "יום ד";
            case Calendar.THURSDAY: return "יום ה";
            case Calendar.FRIDAY: return "יום ו";
            default: return "יום שבת";
        }
    }

    private void refreshSystemMediaMirror() {
        if (mediaMirror == null) return;

        try {
            if (!hasNotificationAccess()) {
                detachMediaController();
                hideMediaMirror();
                showMediaSetup();
                return;
            }
            hideMediaSetup();

            if (mediaSessionManager == null) {
                mediaSessionManager = (MediaSessionManager)
                        getSystemService(MEDIA_SESSION_SERVICE);
            }

            if (mediaSessionManager == null) {
                hideMediaMirror();
                return;
            }

            ComponentName listener = new ComponentName(
                    this, SystemMediaNotificationListener.class);

            // Prefer the session Android is currently routing media-key commands to.
            MediaController selected = null;
            if (Build.VERSION.SDK_INT >= 33) {
                try {
                    MediaSession.Token token = mediaSessionManager.getMediaKeyEventSession();
                    if (token != null) {
                        selected = new MediaController(this, token);
                    }
                } catch (Throwable ignored) {
                }
            }

            // Fallback to the complete active-session list, already ordered by Android.
            if (selected == null) {
                List<MediaController> sessions =
                        mediaSessionManager.getActiveSessions(listener);
                selected = selectBestSession(sessions);
            }

            if (selected == null) {
                detachMediaController();
                hideMediaMirror();
                return;
            }

            if (mediaController == null ||
                    !mediaController.getSessionToken().equals(selected.getSessionToken())) {
                attachMediaController(selected);
            }

            renderMediaMirror();
        } catch (SecurityException ignored) {
            detachMediaController();
            hideMediaMirror();
            showMediaSetup();
        } catch (Throwable ignored) {
            hideMediaMirror();
        }
    }

    private MediaController selectBestSession(List<MediaController> sessions) {
        if (sessions == null || sessions.isEmpty()) return null;

        // Prefer playing/buffering, then paused, preserving Android's priority order.
        for (MediaController controller : sessions) {
            PlaybackState state = controller.getPlaybackState();
            if (state == null) continue;
            int s = state.getState();
            if (s == PlaybackState.STATE_PLAYING ||
                    s == PlaybackState.STATE_BUFFERING) {
                return controller;
            }
        }
        for (MediaController controller : sessions) {
            PlaybackState state = controller.getPlaybackState();
            if (state != null && state.getState() == PlaybackState.STATE_PAUSED) {
                return controller;
            }
        }
        return null;
    }

    private void attachMediaController(MediaController controller) {
        detachMediaController();
        mediaController = controller;
        mediaCallback = new MediaController.Callback() {
            @Override public void onPlaybackStateChanged(PlaybackState state) {
                runOnUiThread(() -> renderMediaMirror());
            }

            @Override public void onMetadataChanged(MediaMetadata metadata) {
                runOnUiThread(() -> renderMediaMirror());
            }

            @Override public void onSessionDestroyed() {
                runOnUiThread(() -> {
                    detachMediaController();
                    hideMediaMirror();
                });
            }
        };

        try {
            mediaController.registerCallback(mediaCallback);
        } catch (Throwable ignored) {
        }
    }

    private void detachMediaController() {
        if (mediaController != null && mediaCallback != null) {
            try {
                mediaController.unregisterCallback(mediaCallback);
            } catch (Throwable ignored) {
            }
        }
        mediaController = null;
        mediaCallback = null;
    }

    private void renderMediaMirror() {
        if (mediaMirror == null || mediaController == null) return;

        try {
            PlaybackState state = mediaController.getPlaybackState();
            MediaMetadata metadata = mediaController.getMetadata();
            if (state == null) {
                hideMediaMirror();
                return;
            }

            int playback = state.getState();
            if (playback != PlaybackState.STATE_PLAYING &&
                    playback != PlaybackState.STATE_BUFFERING &&
                    playback != PlaybackState.STATE_PAUSED) {
                hideMediaMirror();
                return;
            }

            String title = metadata == null ? null :
                    metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            if (title == null || title.trim().isEmpty()) {
                title = metadata == null ? null :
                        metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
            }
            if (title == null || title.trim().isEmpty()) {
                title = "מדיה";
            }

            String artist = metadata == null ? null :
                    metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (artist == null || artist.trim().isEmpty()) {
                artist = mediaController.getPackageName();
            }

            Bitmap art = metadata == null ? null :
                    metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null && metadata != null) {
                art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
            }

            mediaTitle.setText(title);
            mediaSubtitle.setText(artist);
            mediaApp.setText("●");

            if (art != null) {
                albumArt.setImageBitmap(art);
                albumArt.setVisibility(View.VISIBLE);
            } else {
                albumArt.setImageDrawable(null);
                albumArt.setVisibility(View.GONE);
            }

            mediaPlayPause.setText(
                    playback == PlaybackState.STATE_PLAYING ? "Ⅱ" : "▶");

            long actions = state.getActions();
            mediaPrev.setVisibility(
                    (actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0
                            ? View.VISIBLE : View.GONE);
            mediaNext.setVisibility(
                    (actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0
                            ? View.VISIBLE : View.GONE);

            if (mediaProgress != null) {
                long duration = metadata == null ? 0L :
                        metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
                long position = state.getPosition();
                if (position < 0) position = 0;
                if (duration > 0) {
                    mediaProgress.setDuration(duration);
                    mediaProgress.setPosition(position);
                } else {
                    mediaProgress.setDuration(0);
                    mediaProgress.setPosition(0);
                }
            }

            mediaMirror.setVisibility(View.VISIBLE);
            mediaMirror.requestLayout();
        } catch (Throwable ignored) {
            hideMediaMirror();
        }
    }

    private boolean hasNotificationAccess() {
        ComponentName component = new ComponentName(
                this, SystemMediaNotificationListener.class);
        try {
            if (Build.VERSION.SDK_INT >= 27) {
                NotificationManager manager =
                        (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                return manager != null &&
                        manager.isNotificationListenerAccessGranted(component);
            }
        } catch (Throwable ignored) {
        }

        try {
            String enabled = Settings.Secure.getString(
                    getContentResolver(), "enabled_notification_listeners");
            return enabled != null && enabled.contains(component.flattenToString());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void showMediaSetup() {
        if (mediaSetup != null) mediaSetup.setVisibility(View.VISIBLE);
    }

    private void hideMediaSetup() {
        if (mediaSetup != null) mediaSetup.setVisibility(View.GONE);
    }

    private void openNotificationAccessSettings() {
        // Use the top-level Notification Access page for maximum OEM compatibility.
        // Some devices crash when launched with the newer detail-page extra.
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
        } catch (Throwable ignored) {
        }
    }

    private void hideMediaMirror() {
        if (mediaMirror != null) {
            mediaMirror.setVisibility(View.GONE);
        }
    }

    private void sendPlayPause() {
        try {
            if (mediaController == null) return;
            PlaybackState state = mediaController.getPlaybackState();
            if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                mediaController.getTransportControls().pause();
            } else {
                mediaController.getTransportControls().play();
            }
        } catch (Throwable ignored) {
        }
    }

    private void sendPrevious() {
        try {
            if (mediaController != null) {
                mediaController.getTransportControls().skipToPrevious();
            }
        } catch (Throwable ignored) {
        }
    }

    private void sendNext() {
        try {
            if (mediaController != null) {
                mediaController.getTransportControls().skipToNext();
            }
        } catch (Throwable ignored) {
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
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density);
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private static class MediaProgressView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private long duration;
        private long position;

        MediaProgressView(android.content.Context context) {
            super(context);
            paint.setStrokeWidth(3f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        void setDuration(long value) {
            duration = value;
            invalidate();
        }

        void setPosition(long value) {
            position = value;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float y = getHeight() / 2f;
            float start = dpLocal(2);
            float end = w - dpLocal(2);

            paint.setColor(Color.argb(95, 255, 255, 255));
            canvas.drawLine(start, y, end, y, paint);

            float ratio = duration > 0 ? Math.max(0f, Math.min(1f,
                    (float) position / (float) duration)) : 0f;
            float activeEnd = start + (end - start) * ratio;

            paint.setColor(Color.WHITE);
            path.reset();
            path.moveTo(start, y);
            int waves = 16;
            float span = Math.max(dpLocal(18), activeEnd - start);
            for (int i = 0; i <= waves; i++) {
                float x = start + span * i / waves;
                float amp = (i % 2 == 0) ? dpLocal(2) : -dpLocal(2);
                path.lineTo(x, y + amp);
            }
            canvas.drawPath(path, paint);

            canvas.drawCircle(activeEnd, y, dpLocal(5), paint);
        }

        private float dpLocal(float value) {
            return value * getResources().getDisplayMetrics().density;
        }
    }

    private void showFatalError(Throwable t) {
        try {
            TextView error = textView(
                    "Night Screen\n" + t.getClass().getSimpleName(),
                    22, Color.WHITE);
            error.setGravity(Gravity.CENTER);
            error.setBackgroundColor(Color.BLACK);
            setContentView(error);
        } catch (Throwable ignored) {
            finish();
        }
    }
}
