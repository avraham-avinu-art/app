package com.avrahamart.nightscreen;

import android.app.Activity;
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
            new SimpleDateFormat("dd-MM-yyyy", Locale.US);

    private TextView timeText;
    private TextView gregorianText;
    private TextView hebrewText;
    private final int dateTextColor = Color.rgb(220, 220, 220);
    private SharedPreferences prefs;
    private TextView settingsButton;
    private TextView mediaDetails;
    private TextView mediaProgressText;
    private TextView lastMediaButton;
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
        settingsButton = textView("⚙", 20, Color.argb(120, 255, 255, 255));
        settingsButton.setAlpha(0.65f);
        settingsButton.setGravity(Gravity.CENTER);
        settingsButton.setContentDescription("הגדרות");
        settingsButton.setOnClickListener(v -> showSettings());
        top.addView(settingsButton, new LinearLayout.LayoutParams(dp(42), dp(34)));
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(34)));

        timeText = textView("--:--", 82, Color.rgb(247, 247, 247));
        timeText.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        timeText.setGravity(Gravity.CENTER);
        timeText.setAutoSizeTextTypeUniformWithConfiguration(
                dp(48), dp(82), dp(1), android.util.TypedValue.COMPLEX_UNIT_PX);
        LinearLayout.LayoutParams timeParams = matchWrap();
        timeParams.topMargin = dp(18);
        root.addView(timeText, timeParams);

        gregorianText = textView("", 22, dateTextColor);
        gregorianText.setTypeface(
                Typeface.create("sans-serif-medium", Typeface.NORMAL));
        gregorianText.setGravity(Gravity.CENTER);
        gregorianText.setAutoSizeTextTypeUniformWithConfiguration(
                dp(14), dp(22), dp(1), android.util.TypedValue.COMPLEX_UNIT_PX);
        LinearLayout.LayoutParams gregParams = matchWrap();
        gregParams.topMargin = dp(4);
        root.addView(gregorianText, gregParams);

        hebrewText = textView("", 22, dateTextColor);
        hebrewText.setTypeface(
                Typeface.create("sans-serif-medium", Typeface.NORMAL));
        hebrewText.setGravity(Gravity.CENTER);
        hebrewText.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        hebrewText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        hebrewText.setAutoSizeTextTypeUniformWithConfiguration(
                dp(14), dp(22), dp(1), android.util.TypedValue.COMPLEX_UNIT_PX);
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
        mediaTitle.setSelected(true);
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

        // Row 2: clearly visible, bold controls.
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(8), 0, dp(8), 0);

        mediaNext = mediaButton("▶");
        mediaNext.setTextSize(25);
        mediaNext.setTypeface(Typeface.DEFAULT_BOLD);
        mediaNext.setTextColor(Color.WHITE);
        mediaNext.setBackground(makeMediaButtonBackground());
        mediaNext.setOnClickListener(v -> sendNext());
        controls.addView(mediaNext, buttonParams());

        mediaPlayPause = mediaButton("▶");
        mediaPlayPause.setTextSize(22);
        mediaPlayPause.setTypeface(Typeface.DEFAULT_BOLD);
        mediaPlayPause.setTextColor(Color.WHITE);
        mediaPlayPause.setBackground(makeMediaButtonBackground());
        mediaPlayPause.setOnClickListener(v -> sendPlayPause());
        LinearLayout.LayoutParams playParams = buttonParams();
        playParams.leftMargin = dp(8);
        playParams.rightMargin = dp(8);
        controls.addView(mediaPlayPause, playParams);

        mediaPrev = mediaButton("◀");
        mediaPrev.setTextSize(25);
        mediaPrev.setTypeface(Typeface.DEFAULT_BOLD);
        mediaPrev.setTextColor(Color.WHITE);
        mediaPrev.setBackground(makeMediaButtonBackground());
        mediaPrev.setOnClickListener(v -> sendPrevious());
        controls.addView(mediaPrev, buttonParams());

        mediaMirror.addView(controls, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54)));

        lastMediaButton = mediaButton("▶ השמעה אחרונה");
        lastMediaButton.setTextSize(13);
        lastMediaButton.setVisibility(View.GONE);
        lastMediaButton.setOnClickListener(v -> playLastMedia());
        mediaMirror.addView(lastMediaButton, new LinearLayout.LayoutParams(-2, dp(38)));

        int availableWidth = Math.max(
                dp(1),
                getResources().getDisplayMetrics().widthPixels - dp(32));
        LinearLayout.LayoutParams mirrorParams =
                new LinearLayout.LayoutParams(
                        Math.min(dp(760), availableWidth), dp(145));
        mirrorParams.gravity = Gravity.CENTER_HORIZONTAL;
        mirrorParams.bottomMargin = dp(6);
        root.addView(mediaMirror, mirrorParams);
    }

    private android.graphics.drawable.Drawable makeMediaButtonBackground() {
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setColor(getInt("buttonColor", Color.rgb(45,45,45)));
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), getInt("buttonBorderColor", Color.rgb(110,110,110)));
        return bg;
    }

    private LinearLayout.LayoutParams buttonParams() {
        return new LinearLayout.LayoutParams(dp(58), dp(46));
    }

    private TextView mediaButton(String symbol) {
        TextView v = textView(symbol, 24, Color.WHITE);
        v.setGravity(Gravity.CENTER);
        v.setClickable(true);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private void initSettings() {
        prefs.edit().putInt("fontColor", prefs.getInt("fontColor", dateTextColor))
                .putInt("fontSize", prefs.getInt("fontSize", 17))
                .putInt("fontWeight", prefs.getInt("fontWeight", 1))
                .putString("fontFamily", prefs.getString("fontFamily", "sans-serif")).apply();
    }
    private boolean getBool(String k, boolean d) { return prefs != null && prefs.getBoolean(k,d); }
    private int getInt(String k, int d) { return prefs == null ? d : prefs.getInt(k,d); }
    private String formatMs(long ms) {
        long t=Math.max(0,ms/1000); return String.format(Locale.US,"%02d:%02d",(t/60)%60,t%60);
    }
    private void applySettings() {
        if (prefs==null || gregorianText==null) return;
        int color=getInt("fontColor",dateTextColor), size=getInt("fontSize",17), weight=getInt("fontWeight",1);
        Typeface tf=Typeface.create(prefs.getString("fontFamily","sans-serif"),weight==2?Typeface.BOLD:Typeface.NORMAL);
        gregorianText.setVisibility(getBool("showGregorian",true)?View.VISIBLE:View.GONE);
        hebrewText.setVisibility(getBool("showHebrew",true)?View.VISIBLE:View.GONE);
        gregorianText.setTextColor(color); hebrewText.setTextColor(color);
        gregorianText.setTextSize(size); hebrewText.setTextSize(size);
        gregorianText.setTypeface(tf); hebrewText.setTypeface(tf);
        if(mediaTitle!=null){
            mediaTitle.setTypeface(tf); mediaDetails.setTypeface(tf); mediaProgressText.setTypeface(tf);
            mediaTitle.setTextSize(size); mediaTitle.setTextColor(color);
            if(getBool("frameEnabled",false)){
                GradientDrawable bg=new GradientDrawable(); bg.setColor(Color.BLACK);
                bg.setCornerRadius(dp(getInt("frameRadius",10)));
                bg.setStroke(dp(getInt("frameWidth",1)),getInt("frameColor",Color.WHITE));
                mediaMirror.setBackground(bg);
            } else mediaMirror.setBackgroundColor(Color.BLACK);
            mediaNext.setBackground(makeMediaButtonBackground());
            mediaPrev.setBackground(makeMediaButtonBackground());
            mediaPlayPause.setBackground(makeMediaButtonBackground());
        }
    }
    private void showSettings() {
        ScrollView scroll=new ScrollView(this); LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(14),dp(8),dp(14),dp(14));
        box.setBackgroundColor(Color.BLACK); scroll.addView(box);
        TextView h=textView("הגדרות",23,Color.WHITE); h.setGravity(Gravity.CENTER); box.addView(h,new LinearLayout.LayoutParams(-1,dp(46)));
        addSection(box,"גופן");
        addSpinner(box,"צבע",new String[]{"לבן","אפור בהיר","אפור","כחול בהיר"},new String[]{"white","light","gray","blue"},"fontColor");
        addSpinner(box,"גודל",new String[]{"קטן","בינוני","גדול","גדול מאוד"},new String[]{"14","17","20","23"},"fontSize");
        addSpinner(box,"עובי",new String[]{"רגיל","בינוני","מודגש"},new String[]{"0","1","2"},"fontWeight");
        addSpinner(box,"סוג",new String[]{"Sans","Monospace","Serif"},new String[]{"sans-serif","monospace","serif"},"fontFamily");
        addSection(box,"תצוגה");
        addCheck(box,"תאריך לועזי","showGregorian",true); addCheck(box,"תאריך עברי","showHebrew",true); addCheck(box,"נגן","showPlayer",true);
        addSection(box,"אפשרויות נגן");
        addCheck(box,"פרטים נוספים על השיר","showDetails",false);
        addCheck(box,"שורת מיקום + זמן נוכחי / זמן כולל","showProgress",false);
        addCheck(box,"לחצן השמעה אחרונה כשאין שיר פעיל","showLastMedia",false);
        addSection(box,"מסגרת נגן");
        addCheck(box,"הצג מסגרת","frameEnabled",false);
        addSpinner(box,"צבע מסגרת",new String[]{"לבן","אפור","כחול","זהב"},new String[]{"white","gray","blue","gold"},"frameColor");
        addSpinner(box,"עובי מסגרת",new String[]{"דקה","בינונית","עבה"},new String[]{"1","2","3"},"frameWidth");
        addSpinner(box,"עיגול פינות",new String[]{"ישר","עדין","מעוגל"},new String[]{"0","10","20"},"frameRadius");
        Button done=new Button(this); done.setText("סיום"); box.addView(done,new LinearLayout.LayoutParams(-1,dp(50)));
        AlertDialog dialog=new AlertDialog.Builder(this).setView(scroll).create();
        done.setOnClickListener(v->dialog.dismiss()); dialog.setOnDismissListener(d->{applySettings();refreshSystemMediaMirror();});
        dialog.show();
        if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawableResource(android.R.color.black);
            dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(16),dp(520)),
                    Math.min(getResources().getDisplayMetrics().heightPixels-dp(20),dp(700)));}
    }
    private void addSection(LinearLayout b,String s){TextView v=textView(s,16,Color.rgb(170,170,170));v.setGravity(Gravity.RIGHT);b.addView(v,new LinearLayout.LayoutParams(-1,dp(40)));}
    private void addCheck(LinearLayout b,String s,String k,boolean d){CheckBox x=new CheckBox(this);x.setText(s);x.setTextColor(Color.WHITE);x.setTextSize(15);x.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);x.setChecked(getBool(k,d));x.setOnCheckedChangeListener((a,z)->{prefs.edit().putBoolean(k,z).apply();applySettings();});b.addView(x,new LinearLayout.LayoutParams(-1,dp(48)));}
    private void addSpinner(LinearLayout b,String label,String[] names,String[] vals,String key){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView l=textView(label,15,Color.WHITE);l.setGravity(Gravity.RIGHT);
        row.addView(l,new LinearLayout.LayoutParams(0,dp(48),1f));Spinner sp=new Spinner(this);
        sp.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,names));
        String cur=prefs.getString(key,vals[0]);int ix=0;for(int i=0;i<vals.length;i++)if(vals[i].equals(cur))ix=i;sp.setSelection(ix);
        sp.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
                String val=vals[pos];SharedPreferences.Editor e=prefs.edit();
                if(key.equals("fontColor")||key.equals("frameColor")){
                    int col=val.equals("white")?Color.WHITE:val.equals("light")?Color.rgb(220,220,220):val.equals("gray")?Color.GRAY:val.equals("blue")?Color.rgb(80,160,255):Color.rgb(220,180,70);e.putInt(key,col);
                } else if(key.equals("fontSize")||key.equals("fontWeight")||key.equals("frameWidth")||key.equals("frameRadius")) e.putInt(key,Integer.parseInt(val)); else e.putString(key,val);
                e.apply();applySettings();
            } public void onNothingSelected(android.widget.AdapterView<?> p){}
        });row.addView(sp,new LinearLayout.LayoutParams(dp(150),dp(48)));b.addView(row);
    }
    private void playLastMedia(){try{if(mediaController!=null){mediaController.getTransportControls().play();lastMediaButton.setVisibility(View.GONE);mediaPlayPause.setVisibility(View.VISIBLE);}}catch(Throwable ignored){}}
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
                if (getBool("showLastMedia", false) && !lastTitle.isEmpty()) {
                    mediaMirror.setVisibility(View.VISIBLE);
                    mediaTitle.setText(lastTitle);
                    mediaDetails.setText(lastArtist);
                    mediaDetails.setVisibility(getBool("showDetails", false) ? View.VISIBLE : View.GONE);
                    mediaProgressText.setVisibility(View.GONE);
                    mediaNext.setVisibility(View.GONE);
                    mediaPrev.setVisibility(View.GONE);
                    mediaPlayPause.setVisibility(View.GONE);
                    lastMediaButton.setVisibility(View.VISIBLE);
                    applySettings();
                }
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

            title = removeFileExtension(title);
            mediaTitle.setText(title);
            lastTitle = title;
            String artist = metadata == null ? "" : metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
            lastArtist = artist == null ? "" : artist;
            long duration = metadata == null ? 0L : metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
            long position = Math.max(0L, state.getPosition());
            mediaDetails.setText(lastArtist);
            mediaDetails.setVisibility(getBool("showDetails", false) ? View.VISIBLE : View.GONE);
            mediaProgressText.setVisibility(getBool("showProgress", false) && duration > 0 ? View.VISIBLE : View.GONE);
            if (duration > 0) mediaProgressText.setText(formatMs(position) + " / " + formatMs(duration));
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
