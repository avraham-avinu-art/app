package com.avrahamart.nightscreen;

import android.app.Activity;
import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
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

    private LinearLayout mediaMirror;
    private ImageView albumArt;
    private TextView mediaTitle;
    private TextView mediaSubtitle;
    private TextView mediaPlayPause;
    private TextView mediaPrev;
    private TextView mediaNext;

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

        gregorianText = textView("", 22, Color.rgb(190, 190, 190));
        gregorianText.setTypeface(
                Typeface.create("sans-serif-medium", Typeface.NORMAL));
        gregorianText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams gregParams = matchWrap();
        gregParams.topMargin = dp(10);
        root.addView(gregorianText, gregParams);

        hebrewText = textView("", 22, Color.rgb(235, 235, 235));
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

        buildSystemMediaMirror(root);

        setContentView(root);
    }

    private void buildSystemMediaMirror(LinearLayout root) {
        mediaMirror = new LinearLayout(this);
        mediaMirror.setOrientation(LinearLayout.HORIZONTAL);
        mediaMirror.setGravity(Gravity.CENTER_VERTICAL);
        mediaMirror.setPadding(dp(12), dp(10), dp(12), dp(10));
        mediaMirror.setBackgroundColor(Color.rgb(20, 20, 20));
        mediaMirror.setVisibility(View.GONE);

        albumArt = new ImageView(this);
        albumArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mediaMirror.addView(albumArt,
                new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams infoParams =
                new LinearLayout.LayoutParams(0, dp(58), 1f);
        infoParams.leftMargin = dp(12);
        mediaMirror.addView(info, infoParams);

        mediaTitle = textView("", 17, Color.rgb(240, 240, 240));
        mediaTitle.setTypeface(
                Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mediaTitle.setSingleLine(true);
        mediaSubtitle = textView("", 14, Color.rgb(145, 145, 145));
        mediaSubtitle.setSingleLine(true);

        info.addView(mediaTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(30)));
        info.addView(mediaSubtitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(24)));

        mediaPrev = mediaButton("‹");
        mediaPrev.setOnClickListener(v -> sendPrevious());
        mediaMirror.addView(mediaPrev,
                new LinearLayout.LayoutParams(dp(46), dp(46)));

        mediaPlayPause = mediaButton("▶");
        mediaPlayPause.setOnClickListener(v -> sendPlayPause());
        LinearLayout.LayoutParams playParams =
                new LinearLayout.LayoutParams(dp(50), dp(50));
        playParams.leftMargin = dp(6);
        mediaMirror.addView(mediaPlayPause, playParams);

        mediaNext = mediaButton("›");
        mediaNext.setOnClickListener(v -> sendNext());
        LinearLayout.LayoutParams nextParams =
                new LinearLayout.LayoutParams(dp(46), dp(46));
        nextParams.leftMargin = dp(6);
        mediaMirror.addView(mediaNext, nextParams);

        int availableWidth = Math.max(
                dp(1),
                getResources().getDisplayMetrics().widthPixels - dp(48));
        LinearLayout.LayoutParams mirrorParams =
                new LinearLayout.LayoutParams(
                        Math.min(dp(760), availableWidth), dp(78));
        mirrorParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(mediaMirror, mirrorParams);
    }

    private TextView mediaButton(String symbol) {
        TextView v = textView(symbol, 24, Color.BLACK);
        v.setGravity(Gravity.CENTER);
        v.setBackgroundColor(Color.rgb(243, 243, 243));
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
            List<MediaController> sessions =
                    mediaSessionManager.getActiveSessions(listener);

            MediaController selected = selectBestSession(sessions);
            if (selected == null) {
                detachMediaController();
                hideMediaMirror();
                return;
            }

            if (mediaController != selected) {
                attachMediaController(selected);
            }

            renderMediaMirror();
        } catch (SecurityException ignored) {
            detachMediaController();
            hideMediaMirror();
        } catch (Throwable ignored) {
            hideMediaMirror();
        }
    }

    private MediaController selectBestSession(List<MediaController> sessions) {
        if (sessions == null || sessions.isEmpty()) return null;

        for (MediaController controller : sessions) {
            PlaybackState state = controller.getPlaybackState();
            if (state == null) continue;
            int s = state.getState();
            if (s == PlaybackState.STATE_PLAYING ||
                    s == PlaybackState.STATE_BUFFERING ||
                    s == PlaybackState.STATE_PAUSED) {
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

            mediaMirror.setVisibility(View.VISIBLE);
            mediaMirror.requestLayout();
        } catch (Throwable ignored) {
            hideMediaMirror();
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
