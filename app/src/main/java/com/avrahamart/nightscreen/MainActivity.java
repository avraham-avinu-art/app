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
            new SimpleDateFormat("dd.MM.yyyy", Locale.US);

    private TextView timeText;
    private TextView gregorianText;
    private TextView hebrewText;
    private final int dateTextColor = Color.rgb(220, 220, 220);
    private SharedPreferences prefs;
    private TextView settingsButton;
    private TextView mediaDetails;
    private TextView mediaProgressText;
    private SeekBar mediaSeekBar;
    private TextView lastMediaButton;
    private MediaController lastKnownMediaController;
    private ObjectAnimator titleMarqueeAnimator;
    private String lastTitle = "";
    private String lastArtist = "";

    private LinearLayout mediaSetup;
    private LinearLayout mediaMirror;
    private TextView mediaTitle;
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
            prefs = getSharedPreferences("settings", MODE_PRIVATE);
            initSettings();
            getWindow().setFlags(
                    android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
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
        stopTitleMarquee();
        detachMediaController();
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(10), dp(6), dp(10), dp(4));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        settingsButton = textView("⋮", 22, Color.WHITE);
        settingsButton.setAlpha(0.20f);
        settingsButton.setGravity(Gravity.CENTER);
        settingsButton.setContentDescription("הגדרות");
        settingsButton.setOnClickListener(v -> showSettings());
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(dp(30), dp(34));
        settingsParams.rightMargin = -dp(4);
        top.addView(settingsButton, settingsParams);
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(34)));

        timeText = textView("--:--", 82, Color.rgb(247, 247, 247));
        timeText.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        timeText.setGravity(Gravity.CENTER);
        
        LinearLayout.LayoutParams timeParams = matchWrap();
        timeParams.topMargin = dp(18);
        root.addView(timeText, timeParams);

        gregorianText = textView("", 22, dateTextColor);
        gregorianText.setTypeface(
                Typeface.create("sans-serif-medium", Typeface.NORMAL));
        gregorianText.setGravity(Gravity.CENTER);
        
        LinearLayout.LayoutParams gregParams = matchWrap();
        gregParams.topMargin = dp(4);
        root.addView(gregorianText, gregParams);

        hebrewText = textView("", 22, dateTextColor);
        hebrewText.setTypeface(
                Typeface.create("sans-serif-medium", Typeface.NORMAL));
        hebrewText.setGravity(Gravity.CENTER);
        hebrewText.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        hebrewText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        
        LinearLayout.LayoutParams hebParams = matchWrap();
        hebParams.topMargin = dp(3);
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
        mediaMirror.setGravity(Gravity.CENTER);
        mediaMirror.setPadding(dp(10), dp(4), dp(10), dp(4));
        mediaMirror.setBackgroundColor(Color.BLACK);
        mediaMirror.setVisibility(View.GONE);

        // Row 1: song title only. It scrolls automatically when it does not fit.
        mediaTitle = textView("", 17, Color.WHITE);
        mediaTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mediaTitle.setSingleLine(true);
        mediaTitle.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        mediaTitle.setMarqueeRepeatLimit(-1);
        mediaTitle.setSelected(false);
        mediaTitle.setHorizontallyScrolling(true);
        mediaTitle.setGravity(Gravity.CENTER);
        mediaTitle.setHorizontallyScrolling(true);
        mediaMirror.addView(mediaTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(38)));

        mediaDetails = textView("", 13, Color.rgb(175, 175, 175));
        mediaDetails.setGravity(Gravity.CENTER);
        mediaDetails.setSingleLine(true);
        mediaDetails.setVisibility(View.GONE);
        mediaMirror.addView(mediaDetails, new LinearLayout.LayoutParams(-1, dp(22)));

        mediaProgressText = textView("", 12, Color.rgb(160, 160, 160));
        mediaProgressText.setGravity(Gravity.CENTER);
        mediaProgressText.setSingleLine(true);
        mediaProgressText.setVisibility(View.GONE);
        mediaMirror.addView(mediaProgressText, new LinearLayout.LayoutParams(-1, dp(20)));

        mediaSeekBar = new SeekBar(this);
        mediaSeekBar.setVisibility(View.GONE);
        mediaSeekBar.setPadding(dp(4), 0, dp(4), 0);
        // Progress fills visually from left to right.
        mediaSeekBar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        mediaSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaController != null) {
                    PlaybackState st = mediaController.getPlaybackState();
                    if (st != null) {
                        long duration = st.getState() == PlaybackState.STATE_NONE ? 0L :
                                (mediaController.getMetadata() == null ? 0L :
                                mediaController.getMetadata().getLong(MediaMetadata.METADATA_KEY_DURATION));
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
        mediaMirror.addView(mediaSeekBar, new LinearLayout.LayoutParams(-1, dp(20)));

        // Row 2: clearly visible, modern, subtle controls.
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(8), 0, dp(8), 0);

        mediaPrev = mediaButton("|◀");
        mediaPrev.setTextSize(25);
        mediaPrev.setTypeface(Typeface.DEFAULT);
        mediaPrev.setTextColor(Color.WHITE);
        mediaPrev.setBackgroundColor(Color.TRANSPARENT);
        mediaPrev.setOnClickListener(v -> sendPrevious());
        controls.addView(mediaPrev, flatControlParams());

        mediaPlayPause = mediaButton("Ⅱ");
        mediaPlayPause.setTextSize(32);
        mediaPlayPause.setTypeface(Typeface.DEFAULT_BOLD);
        mediaPlayPause.setTextColor(Color.WHITE);
        mediaPlayPause.setBackgroundColor(Color.TRANSPARENT);
        mediaPlayPause.setOnClickListener(v -> sendPlayPause());
        LinearLayout.LayoutParams playParams = flatControlParams();
        playParams.leftMargin = dp(18);
        playParams.rightMargin = dp(18);
        controls.addView(mediaPlayPause, playParams);

        mediaNext = mediaButton("▶|");
        mediaNext.setTextSize(25);
        mediaNext.setTypeface(Typeface.DEFAULT);
        mediaNext.setTextColor(Color.WHITE);
        mediaNext.setBackgroundColor(Color.TRANSPARENT);
        mediaNext.setOnClickListener(v -> sendNext());
        controls.addView(mediaNext, flatControlParams());

        mediaMirror.addView(controls, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        lastMediaButton = mediaButton("play\\n⏻");
        lastMediaButton.setTextSize(15);
        lastMediaButton.setGravity(Gravity.CENTER);
        lastMediaButton.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        lastMediaButton.setTextColor(Color.WHITE);
        lastMediaButton.setAlpha(0.50f);
        lastMediaButton.setBackgroundColor(Color.TRANSPARENT);
        lastMediaButton.setVisibility(View.GONE);
        lastMediaButton.setOnClickListener(v -> playLastMedia());
        mediaMirror.addView(lastMediaButton, new LinearLayout.LayoutParams(dp(72), dp(62)));

        int availableWidth = Math.max(
                dp(1),
                getResources().getDisplayMetrics().widthPixels - dp(32));
        LinearLayout.LayoutParams mirrorParams =
                new LinearLayout.LayoutParams(
                        Math.min(dp(760), availableWidth), dp(200));
        mirrorParams.gravity = Gravity.CENTER_HORIZONTAL;
        mirrorParams.bottomMargin = dp(6);
        root.addView(mediaMirror, mirrorParams);
    }

    private android.graphics.drawable.Drawable makeMediaButtonBackground() {
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.argb(180, 45,45,45));
        bg.setCornerRadius(dp(23));
        bg.setStroke(dp(1), Color.argb(90,255,255,255));
        return bg;
    }

    private LinearLayout.LayoutParams flatControlParams() {
        return new LinearLayout.LayoutParams(dp(54), dp(50));
    }

    private TextView mediaButton(String symbol) {
        TextView v = textView(symbol, 24, Color.WHITE);
        v.setGravity(Gravity.CENTER);
        v.setClickable(true);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private void initSettings() {
        if (prefs == null) {
            prefs = getSharedPreferences("settings", MODE_PRIVATE);
        }

        SharedPreferences.Editor e = prefs.edit();

        e.putInt("fontColor", getInt("fontColor", dateTextColor));
        e.putInt("fontSize", getInt("fontSize", 17));
        e.putInt("clockDateSize", getInt("clockDateSize", 48));
        e.putInt("playerSize", getInt("playerSize", 17));
        e.putInt("fontWeight", getInt("fontWeight", 1));
        e.putString("fontFamily", getString("fontFamily", "sans-serif"));

        e.putBoolean("showGregorian", getBool("showGregorian", true));
        e.putBoolean("showHebrew", getBool("showHebrew", true));
        e.putBoolean("showPlayer", getBool("showPlayer", true));
        e.putBoolean("showDetails", getBool("showDetails", false));
        e.putBoolean("showProgress", getBool("showProgress", false));
        e.putBoolean("showLastMedia", getBool("showLastMedia", false));
        lastTitle = getString("lastTitle", lastTitle);
        lastArtist = getString("lastArtist", lastArtist);

        e.putBoolean("frameEnabled", getBool("frameEnabled", false));
        e.putInt("frameColor", getInt("frameColor", Color.WHITE));
        e.putInt("frameWidth", getInt("frameWidth", 1));
        e.putInt("frameRadius", getInt("frameRadius", 10));
        e.putInt("buttonColor", getInt("buttonColor", Color.rgb(45,45,45)));
        e.putInt("buttonBorderColor", getInt("buttonBorderColor", Color.rgb(110,110,110)));
        e.apply();
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

    private String formatMs(long ms) {
        long t = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%02d:%02d", (t / 60) % 60, t % 60);
    }

    private void applySettings() {
        if (prefs == null || gregorianText == null) return;

        int color = getInt("fontColor", dateTextColor);
        int size = getInt("fontSize", 17);
        int clockDateSize = getInt("clockDateSize", 48);
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

        if (mediaTitle != null) {
            mediaTitle.setTypeface(tf);
            mediaDetails.setTypeface(tf);
            mediaProgressText.setTypeface(tf);
            mediaTitle.setTextSize(playerSize);
            mediaDetails.setTextSize(Math.max(11, playerSize - 3));
            mediaProgressText.setTextSize(Math.max(10, playerSize - 4));
            mediaTitle.setTextColor(color);
            mediaDetails.setTextColor(color);
            mediaProgressText.setTextColor(color);
            mediaSeekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
            mediaSeekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(color));

            if (getBool("frameEnabled", false)) {
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
            if (lastMediaButton != null) {
                lastMediaButton.setText("play\n⏻");
                lastMediaButton.setTextColor(Color.WHITE);
                lastMediaButton.setAlpha(0.50f);
                lastMediaButton.setBackgroundColor(Color.TRANSPARENT);
            }
        }
    }

    private void showSettings() {
        try {
            if (prefs == null) initSettings();

            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(9), dp(2), dp(9), dp(7));
            box.setBackgroundColor(Color.rgb(248, 249, 251));
            scroll.addView(box);

            TextView h = textView("הגדרות", 22, Color.rgb(25, 28, 35));
            h.setGravity(Gravity.CENTER);
            h.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            box.addView(h, new LinearLayout.LayoutParams(-1, dp(36)));

            addSection(box, "תצוגת שעון ותאריך");
            addSpinner(box, "גודל שעה ותאריך",
                    new String[]{"קטן","בינוני","גדול","גדול מאוד"},
                    new String[]{"38","48","60","72"}, "clockDateSize");

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
            addCheck(box, "פרטי אמן (כשקיים בלבד)", "showDetails", false);
            addCheck(box, "שורת מיקום + זמן", "showProgress", false);
            addCheck(box, "לחצן השמעה אחרונה כשאין שיר פעיל", "showLastMedia", false);

            addSection(box, "מסגרת נגן");
            addCheck(box, "הצג מסגרת", "frameEnabled", false);
            addSpinner(box, "צבע מסגרת",
                    new String[]{"אפור כחול","כסוף","סגול עדין","זהב עדין"},
                    new String[]{"slate","silver","purple","gold"}, "frameColor");
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
            doneBg.setCornerRadius(dp(14));
            done.setBackground(doneBg);
            LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(-1, dp(40));
            doneParams.topMargin = dp(7);
            box.addView(done, doneParams);

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setView(scroll)
                    .create();

            done.setOnClickListener(v -> dialog.dismiss());
            dialog.setOnDismissListener(d -> {
                applySettings();
                refreshSystemMediaMirror();
            });

            dialog.show();

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.white);
                dialog.getWindow().setDimAmount(0.45f);
                dialog.getWindow().setLayout(
                        Math.min(getResources().getDisplayMetrics().widthPixels - dp(24), dp(560)),
                        Math.min(getResources().getDisplayMetrics().heightPixels - dp(18), dp(700)));
            }
        } catch (Throwable t) {
            showFatalError(t);
        }
    }

    private void addSection(LinearLayout b, String s) {
        TextView v = textView(s, 15, Color.rgb(65, 92, 125));
        v.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        v.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        v.setPadding(dp(4), 0, dp(4), 0);
        b.addView(v, new LinearLayout.LayoutParams(-1, dp(28)));
    }

    private void addCheck(LinearLayout b, String s, String k, boolean d) {
        CheckBox x = new CheckBox(this);
        x.setText(s);
        x.setTextColor(Color.rgb(35, 38, 45));
        x.setTextSize(15);
        x.setPadding(dp(2), 0, dp(2), 0);
        x.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        if (Build.VERSION.SDK_INT >= 21) {
            x.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.rgb(55, 95, 145)));
        }
        x.setChecked(getBool(k, d));
        x.setOnCheckedChangeListener((a, z) -> {
            try {
                prefs.edit().putBoolean(k, z).apply();
                applySettings();
            } catch (Throwable ignored) {
            }
        });
        b.addView(x, new LinearLayout.LayoutParams(-1, dp(38)));
    }

    private void addSpinner(LinearLayout b, String label, String[] names,
                            String[] vals, String key) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView l = textView(label, 15, Color.rgb(45, 48, 55));
        l.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.addView(l, new LinearLayout.LayoutParams(0, dp(38), 1f));

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
                v.setMinHeight(dp(36));
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

        sp.setSelection(ix);
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

                            e.apply();
                            applySettings();
                        } catch (Throwable ignored) {
                        }
                    }

                    @Override public void onNothingSelected(
                            android.widget.AdapterView<?> parent) {
                    }
                });

        row.addView(sp, new LinearLayout.LayoutParams(dp(150), dp(38)));
        b.addView(row);
    }

    private int colorValue(String value) {
        if ("white".equals(value)) return Color.WHITE;
        if ("light".equals(value)) return Color.rgb(220,220,220);
        if ("gray".equals(value)) return Color.GRAY;
        if ("blue".equals(value)) return Color.rgb(120,165,205);
        if ("slate".equals(value)) return Color.rgb(100,125,150);
        if ("silver".equals(value)) return Color.rgb(155,165,175);
        if ("purple".equals(value)) return Color.rgb(145,130,165);
        if ("gold".equals(value)) return Color.rgb(190,165,105);
        return Color.WHITE;
    }

    private void playLastMedia() {
        try {
            MediaController controller =
                    mediaController != null ? mediaController : lastKnownMediaController;

            if (controller == null && mediaSessionManager != null) {
                try {
                    ComponentName listener =
                            new ComponentName(this, SystemMediaNotificationListener.class);
                    List<MediaController> sessions =
                            mediaSessionManager.getActiveSessions(listener);
                    controller = selectPlayableSession(sessions);
                    if (controller != null) {
                        lastKnownMediaController = controller;
                        attachMediaController(controller);
                    }
                } catch (Throwable ignored) {
                }
            }

            if (controller == null) return;

            MediaController.TransportControls controls = controller.getTransportControls();
            if (controls == null) return;

            controls.play();
            if (lastMediaButton != null) lastMediaButton.setVisibility(View.GONE);
            if (mediaPlayPause != null) {
                mediaPlayPause.setVisibility(View.VISIBLE);
                mediaPlayPause.setText("Ⅱ");
            }
            renderMediaMirror();
        } catch (Throwable ignored) {
        }
    }

    private MediaController selectPlayableSession(List<MediaController> sessions) {
        if (sessions == null || sessions.isEmpty()) return null;

        for (MediaController controller : sessions) {
            try {
                PlaybackState state = controller.getPlaybackState();
                if (state != null &&
                        (state.getState() == PlaybackState.STATE_PAUSED ||
                         state.getState() == PlaybackState.STATE_PLAYING ||
                         state.getState() == PlaybackState.STATE_BUFFERING)) {
                    return controller;
                }
            } catch (Throwable ignored) {
            }
        }

        return sessions.get(0);
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
        return String.format(Locale.US, "%d:%02d",
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
                hideMediaMirror();
                if (getBool("showLastMedia", false)) {
                    mediaMirror.setVisibility(View.VISIBLE);
                    stopTitleMarquee();
                    mediaTitle.setText("");
                    mediaDetails.setText("");
                    mediaDetails.setVisibility(View.GONE);
                    mediaProgressText.setVisibility(View.GONE);
                    mediaSeekBar.setVisibility(View.GONE);
                    mediaNext.setVisibility(View.GONE);
                    mediaPrev.setVisibility(View.GONE);
                    mediaPlayPause.setVisibility(View.GONE);
                    lastMediaButton.setVisibility(View.VISIBLE);
                    applySettings();
                }
                return;
            }

            lastKnownMediaController = selected;
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
                    lastKnownMediaController = mediaController;
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

            title = removeFileExtension(title);
            mediaTitle.setText(title);
            stopTitleMarquee();
            final String marqueeTitle = title;
            mediaTitle.post(() -> {
                try {
                    float textWidth = mediaTitle.getPaint().measureText(marqueeTitle);
                    float available = Math.max(0, mediaTitle.getWidth() - mediaTitle.getPaddingLeft() - mediaTitle.getPaddingRight());
                    float threshold = mediaTitle.getPaint().measureText("XX");
                    float overflow = textWidth - available;
                    if (overflow > threshold) {
                        mediaTitle.setEllipsize(null);
                        mediaTitle.setHorizontallyScrolling(true);
                        mediaTitle.setScrollX(0);
                        titleMarqueeAnimator = ObjectAnimator.ofInt(
                                mediaTitle, "scrollX", 0, Math.max(1, (int)Math.ceil(overflow)));
                        long duration = Math.max(14000L, Math.min(30000L, (long)(overflow * 45L)));
                        titleMarqueeAnimator.setDuration(duration);
                        titleMarqueeAnimator.setRepeatMode(ObjectAnimator.REVERSE);
                        titleMarqueeAnimator.setRepeatCount(ObjectAnimator.INFINITE);
                        titleMarqueeAnimator.start();
                    } else {
                        mediaTitle.setEllipsize(TextUtils.TruncateAt.END);
                        mediaTitle.setHorizontallyScrolling(false);
                        mediaTitle.setScrollX(0);
                    }
                } catch (Throwable ignored) {}
            });
            lastTitle = title;
            String artist = metadata == null ? "" : metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
            lastArtist = artist == null ? "" : artist.trim();
            prefs.edit().putString("lastTitle", lastTitle).putString("lastArtist", lastArtist).apply();
            long duration = metadata == null ? 0L : metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
            long position = Math.max(0L, state.getPosition());
            mediaDetails.setText(lastArtist);
            mediaDetails.setVisibility(getBool("showDetails", false) && !lastArtist.trim().isEmpty() ? View.VISIBLE : View.GONE);
            boolean showProgress = getBool("showProgress", false) && duration > 0;
            mediaProgressText.setVisibility(showProgress ? View.VISIBLE : View.GONE);
            mediaSeekBar.setVisibility(showProgress ? View.VISIBLE : View.GONE);
            if (duration > 0) {
                // Total time first, current time second.
                mediaProgressText.setText(formatMs(duration) + "  /  " + formatMs(position));
                mediaSeekBar.setMax(1000);
                mediaSeekBar.setProgress((int)Math.min(1000L, (position * 1000L) / duration));
            }
            mediaPlayPause.setVisibility(View.VISIBLE);
            lastMediaButton.setVisibility(View.GONE);
            mediaPlayPause.setText(
                    playback == PlaybackState.STATE_PLAYING ? "Ⅱ" : "▶");

            long actions = state.getActions();
            mediaPrev.setVisibility(
                    (actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0
                            ? View.VISIBLE : View.GONE);
            mediaNext.setVisibility(
                    (actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0
                            ? View.VISIBLE : View.GONE);

            mediaMirror.setVisibility(getBool("showPlayer", true) ? View.VISIBLE : View.GONE);
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

    private String removeFileExtension(String title) {
        if (title == null) return "";
        String value = title.trim();
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        int dot = value.lastIndexOf('.');
        if (dot > slash + 0 && dot < value.length() - 1) {
            return value.substring(0, dot);
        }
        return value;
    }

    private void stopTitleMarquee() {
        try {
            if (titleMarqueeAnimator != null) {
                titleMarqueeAnimator.cancel();
                titleMarqueeAnimator = null;
            }
        } catch (Throwable ignored) {
        }
        if (mediaTitle != null) {
            mediaTitle.setScrollX(0);
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
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController controller = decor.getWindowInsetsController();
            if (controller != null) {
                controller.hide(android.view.WindowInsets.Type.statusBars()
                        | android.view.WindowInsets.Type.navigationBars()
                        | android.view.WindowInsets.Type.captionBar());
                controller.setSystemBarsBehavior(
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUi();
        }
    }

    private void showFatalError(Throwable t) {
        try {
            TextView error = textView(
                    "שומר מסך\n" + t.getClass().getSimpleName(),
                    22, Color.WHITE);
            error.setGravity(Gravity.CENTER);
            error.setBackgroundColor(Color.BLACK);
            setContentView(error);
        } catch (Throwable ignored) {
            finish();
        }
    }
}
