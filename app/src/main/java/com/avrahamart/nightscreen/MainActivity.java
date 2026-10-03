package com.avrahamart.nightscreen;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter;
import com.kosherjava.zmanim.hebrewcalendar.JewishDate;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_AUDIO = 1001;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Calendar now = Calendar.getInstance();
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat gregorianFormat =
            new SimpleDateFormat("dd/MM/yyyy", Locale.US);

    private TextView timeText;
    private TextView gregorianText;
    private TextView hebrewText;
    private TextView playerStatus;
    private TextView playButton;

    private MediaPlayer mediaPlayer;
    private Uri selectedAudioUri;
    private String selectedAudioName = "";

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateClock();
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
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        releasePlayer();
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

        gregorianText = textView("", 20, Color.rgb(184, 184, 184));
        gregorianText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams gregParams = matchWrap();
        gregParams.topMargin = dp(8);
        root.addView(gregorianText, gregParams);

        hebrewText = textView("", 25, Color.rgb(234, 234, 234));
        hebrewText.setTypeface(
                Typeface.create("sans-serif-medium", Typeface.NORMAL));
        hebrewText.setGravity(Gravity.CENTER);
        hebrewText.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        hebrewText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        LinearLayout.LayoutParams hebParams = matchWrap();
        hebParams.topMargin = dp(10);
        root.addView(hebrewText, hebParams);

        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        buildPlayer(root);

        setContentView(root);
    }

    private void buildPlayer(LinearLayout root) {
        LinearLayout player = new LinearLayout(this);
        player.setOrientation(LinearLayout.HORIZONTAL);
        player.setGravity(Gravity.CENTER_VERTICAL);
        player.setPadding(dp(18), 0, dp(12), 0);
        player.setBackgroundColor(Color.rgb(17, 17, 17));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = textView("נגן", 19, Color.rgb(231, 231, 231));
        playerStatus = textView("בחר קובץ שמע", 14, Color.rgb(125, 125, 125));
        playerStatus.setSingleLine(true);
        info.addView(label, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(30)));
        info.addView(playerStatus, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(24)));

        player.addView(info, new LinearLayout.LayoutParams(0, dp(72), 1f));

        TextView chooseButton = createPlayerButton("⋯");
        chooseButton.setOnClickListener(v -> chooseAudio());
        player.addView(chooseButton, new LinearLayout.LayoutParams(dp(52), dp(52)));

        playButton = createPlayerButton("▶");
        playButton.setOnClickListener(v -> togglePlayback());
        LinearLayout.LayoutParams playParams =
                new LinearLayout.LayoutParams(dp(52), dp(52));
        playParams.leftMargin = dp(10);
        player.addView(playButton, playParams);

        int availableWidth = Math.max(
                dp(1),
                getResources().getDisplayMetrics().widthPixels - dp(48));
        LinearLayout.LayoutParams playerParams =
                new LinearLayout.LayoutParams(
                        Math.min(dp(760), availableWidth), dp(82));
        playerParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(player, playerParams);
    }

    private TextView createPlayerButton(String symbol) {
        TextView button = new TextView(this);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(22);
        button.setTextColor(Color.BLACK);
        button.setBackgroundColor(Color.rgb(243, 243, 243));
        button.setText(symbol);
        button.setClickable(true);
        return button;
    }

    private void chooseAudio() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        startActivityForResult(intent, REQUEST_AUDIO);
    }

    private void togglePlayback() {
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                    playButton.setText("▶");
                    playerStatus.setText(
                            selectedAudioName.isEmpty() ? "מושהה" : selectedAudioName);
                } else {
                    mediaPlayer.start();
                    playButton.setText("Ⅱ");
                    playerStatus.setText(
                            selectedAudioName.isEmpty() ? "מנגן עכשיו" : selectedAudioName);
                }
                return;
            }

            chooseAudio();
        } catch (Throwable t) {
            releasePlayer();
            playerStatus.setText("לא ניתן להפעיל את הקובץ");
            playButton.setText("▶");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_AUDIO || resultCode != RESULT_OK ||
                data == null || data.getData() == null) {
            return;
        }

        Uri uri = data.getData();
        try {
            releasePlayer();
            selectedAudioUri = uri;
            selectedAudioName = uri.getLastPathSegment() == null
                    ? "שמע"
                    : uri.getLastPathSegment();

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, uri);
            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                playButton.setText("Ⅱ");
                playerStatus.setText(selectedAudioName);
            });
            mediaPlayer.setOnCompletionListener(mp -> {
                playButton.setText("▶");
                playerStatus.setText(selectedAudioName);
            });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                releasePlayer();
                playerStatus.setText("לא ניתן לנגן את הקובץ");
                playButton.setText("▶");
                return true;
            });
            playerStatus.setText("טוען...");
            mediaPlayer.prepareAsync();
        } catch (Throwable t) {
            releasePlayer();
            playerStatus.setText("לא ניתן לפתוח את הקובץ");
            playButton.setText("▶");
        }
    }

    private void releasePlayer() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
            } catch (Throwable ignored) {
            }
            try {
                mediaPlayer.reset();
            } catch (Throwable ignored) {
            }
            try {
                mediaPlayer.release();
            } catch (Throwable ignored) {
            }
            mediaPlayer = null;
        }
        if (playButton != null) {
            playButton.setText("▶");
        }
    }

    private void updateClock() {
        now.setTimeInMillis(System.currentTimeMillis());
        timeText.setText(timeFormat.format(now.getTime()));
        gregorianText.setText(gregorianFormat.format(now.getTime()));

        try {
            JewishDate jewishDate = new JewishDate(now);
            HebrewDateFormatter formatter = new HebrewDateFormatter();
            formatter.setHebrewFormat(true);
            formatter.setUseGershGershayim(true);
            formatter.setUseLongHebrewYears(true);
            hebrewText.setText(formatter.format(jewishDate));
        } catch (Throwable t) {
            hebrewText.setText("תאריך עברי לא זמין");
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
