package com.example.sltv6;

import android.Manifest;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;

import org.tensorflow.lite.Interpreter;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.animation.PathInterpolator;



import java.io.FileInputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private volatile boolean isRecreating = false;
    private String currentTheme;
    private PreviewView previewView;
    private OverlayView overlayView;
    private TextView tvLetter;
    private TextView tvSentence;
    private TextToSpeech tts;

    private ImageButton clearBtn;
    private ImageButton playBtn;

    private LinearLayout mainContent;
    private LinearLayout settingsContent;
    private boolean isSettingsOpen = false;

    private LinearLayout helpContent;
    private boolean isHelpOpen = false;
    private float swipeTouchStartX = 0f;
    private static final int SWIPE_THRESHOLD_PERCENT = 15; 

    private boolean showSkeleton = true;


    private Interpreter tflite;
    private final char[] letters = {'.', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
                                    'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
                                    ' ', '~'};


    int numLetters = letters.length;
    private static final float CONFIDENCE_THRESHOLD = 0.8f;

    private String lastRecordedLetter = "";
    private long lastRecordTime = 0;

    private boolean useFrontCamera = true;

    private ExecutorService cameraExecutor;

    private HandLandmarker handLandmarker;
    private YuvToRgbConverter yuvConverter;


    private ProcessCameraProvider cameraProvider;
    private static final int CAMERA_PERMISSION = 100;
    private String currentLetter = "";
    private long letterStartTime = 0;
    private long lastSpeakTime = 0;
    private long minDisplayTimeMs = 400;
    private long speakIntervalMs = 1500;
    private boolean ttsCurrentLetter = true;
    private boolean isPreviousTssCurrentLetterOn = true;
    private boolean playAudio = false;

    
    private final java.util.Deque<String> predictionHistory = new java.util.ArrayDeque<>(7);
    private static final int SMOOTHING_WINDOW = 7;
    private static final int MIN_STABLE_PREDICTIONS = 4;  

    private final Matrix rotationMatrix = new Matrix();

    private boolean isRecording = false;
    private float centerVisualOffset = 0f;   
    private float bottomOffset = 0f;          
    private int widthRecord;
    private int widthStop;

    private ImageButton settingsBtn;

    private MaterialAutoCompleteTextView autoCompleteTextView;
    private static final int HIT_AREA_PADDING_PX = 50;

    private boolean hasMovedToBottom = false;

    private List<LanguageItem> allLanguages;
    private ArrayAdapter<LanguageItem> languageAdapter;
    private String selectedLanguageCode; 

    private MaterialAutoCompleteTextView themesAutoComplete;
    private List<ThemeItem> allThemes;
    private ArrayAdapter<ThemeItem> themeAdapter;

    private final java.util.Map<String, Integer> smoothingCounts = new java.util.HashMap<>();
    private Context cachedLocaleContext;

    private static final String MODEL_NAME = "asl_letters_model.tflite";

    static final PathInterpolator IOS_SPRING =
            new PathInterpolator(0.34f, 1.56f, 0.64f, 1f);


    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {



        super.onCreate(savedInstanceState);
        applyTheme(loadSavedTheme());
        setContentView(R.layout.activity_main);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        previewView = findViewById(R.id.previewView);
        RelativeLayout recordContainer = findViewById(R.id.recordContainer);
        overlayView = findViewById(R.id.overlayView);
        overlayView.setMirror(useFrontCamera);
        tvLetter = findViewById(R.id.tvLetter);
        tvSentence = findViewById(R.id.tvSentence);
        ImageButton muteLetterBtn = findViewById(R.id.muteLetter);
        ImageButton flipBtn = findViewById(R.id.flip);
        ImageButton helpBtn = findViewById(R.id.help);
        playBtn = findViewById(R.id.play);
        settingsBtn = findViewById(R.id.settings);
        clearBtn = findViewById(R.id.clear);
        clearBtn.setVisibility(View.GONE);
        playBtn.setVisibility(View.GONE);
        Button recordBtn = findViewById(R.id.record);
        autoCompleteTextView = findViewById(R.id.languages);
        TextInputLayout languageLayout = findViewById(R.id.languageTextInputLayout);
        mainContent = findViewById(R.id.mainContent);
        settingsContent = findViewById(R.id.settingsContent);
        helpContent = findViewById(R.id.helpContent);

        minDisplayTimeMs = getSharedPreferences("settings", MODE_PRIVATE)
                .getLong("min_display_time", 400);

        speakIntervalMs = getSharedPreferences("settings", MODE_PRIVATE)
                .getLong("speak_interval_time", 1500);

        showSkeleton = getSharedPreferences("settings", MODE_PRIVATE)
                .getBoolean("show_skeleton", true);
        overlayView.setSkeletonEnabled(showSkeleton);

        MaterialSwitch skeletonSwitch = findViewById(R.id.skeletonSwitch);
        skeletonSwitch.setChecked(showSkeleton);
        skeletonSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            showSkeleton = isChecked;
            overlayView.setSkeletonEnabled(isChecked);
            getSharedPreferences("settings", MODE_PRIVATE)
                    .edit()
                    .putBoolean("show_skeleton", isChecked)
                    .apply();
            bounceView(skeletonSwitch);
        });


        Slider detectionSlider = findViewById(R.id.holdDurationSlider);
        detectionSlider.setValue(minDisplayTimeMs);

        Slider speechSlider = findViewById(R.id.speechDelaySlider);
        speechSlider.setValue(speakIntervalMs);

        detectionSlider.addOnChangeListener((slider, value, fromUser) -> {
            minDisplayTimeMs = (long) value;
            getSharedPreferences("settings", MODE_PRIVATE)
                    .edit()
                    .putLong("min_display_time", minDisplayTimeMs)
                    .apply();
        });

        speechSlider.addOnChangeListener((slider, value, fromUser) -> {
            speakIntervalMs = (long) value;
            getSharedPreferences("settings", MODE_PRIVATE)
                    .edit()
                    .putLong("speak_interval_time", speakIntervalMs)
                    .apply();
        });

        findViewById(R.id.holdDuration).setOnClickListener(v -> {
            minDisplayTimeMs = 400;
            detectionSlider.setValue(400f);
            getSharedPreferences("settings", MODE_PRIVATE)
                    .edit()
                    .putLong("min_display_time", 400)
                    .apply();
        });

        findViewById(R.id.speechDelay).setOnClickListener(v -> {
            speakIntervalMs = 1500;
            speechSlider.setValue(1500f);
            getSharedPreferences("settings", MODE_PRIVATE)
                    .edit()
                    .putLong("speak_interval_time", 1500)
                    .apply();
        });

        selectedLanguageCode = loadSavedLanguage(); 
        cachedLocaleContext = LocaleHelper.wrap(this, getLocaleFromCode(selectedLanguageCode));

        allLanguages = new ArrayList<>();
        allLanguages.add(new LanguageItem("en", getString(R.string.lang_english)));
        allLanguages.add(new LanguageItem("id", getString(R.string.lang_indonesia)));

        languageAdapter = new ArrayAdapter<>(
                this,
                R.layout.languages_dropdown_item,
                R.id.tv_languages_dropdown,
                new ArrayList<>()
        ) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                View separator = view.findViewById(R.id.separator);
                if (separator != null) {
                    separator.setVisibility(
                            position == getCount() - 1 ? View.GONE : View.VISIBLE
                    );
                }
                return view;
            }

            @NonNull
            @Override
            public android.widget.Filter getFilter() {
                return new android.widget.Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        FilterResults results = new FilterResults();
                        results.values = null;
                        results.count = 0;
                        return results;
                    }

                    @Override
                    protected void publishResults(CharSequence constraint, FilterResults results) {
                        
                    }
                };
            }
        };
        refreshLanguageDropdown();
        autoCompleteTextView.setAdapter(languageAdapter);
        autoCompleteTextView.setThreshold(Integer.MAX_VALUE); 
        autoCompleteTextView.setText("", false);

        allLanguages.stream()
                .filter(item -> item.code.equals(selectedLanguageCode))
                .findFirst()
                .ifPresent(selectedItem -> autoCompleteTextView.post(() -> {
                    autoCompleteTextView.setText(selectedItem.label, false);
                    
                    refreshLanguageDropdown();
                }));


        languageLayout.setEndIconOnClickListener(null);

        themesAutoComplete = findViewById(R.id.themes);
        TextInputLayout themeLayout = findViewById(R.id.ThemeInputLayout);

        String savedTheme = loadSavedTheme();
        currentTheme = savedTheme;

        allThemes = new ArrayList<>();
        allThemes.add(new ThemeItem("dark", getString(R.string.darkmode)));
        allThemes.add(new ThemeItem("light", getString(R.string.lightmode)));
        allThemes.add(new ThemeItem("system", getString(R.string.systemdefault)));

        themeAdapter = new ArrayAdapter<>(
                this,
                R.layout.themes_dropdown_item,
                R.id.tv_themes_dropdown,
                new ArrayList<>()
        ) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                View separator = view.findViewById(R.id.separator);
                if (separator != null) {
                    separator.setVisibility(
                            position == getCount() - 1 ? View.GONE : View.VISIBLE
                    );
                }
                return view;
            }

            @NonNull
            @Override
            public android.widget.Filter getFilter() {
                return new android.widget.Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        FilterResults results = new FilterResults();
                        results.values = null;
                        results.count = 0;
                        return results;
                    }

                    @Override
                    protected void publishResults(CharSequence constraint, FilterResults results) {
                        
                    }
                };
            }
        };

        refreshThemeDropdown(savedTheme);
        themesAutoComplete.setAdapter(themeAdapter);
        themesAutoComplete.setThreshold(Integer.MAX_VALUE);
        themesAutoComplete.setText("", false);


        allThemes.stream()
                .filter(item -> item.mode.equals(savedTheme))
                .findFirst()
                .ifPresent(selected -> {
                    themesAutoComplete.setText(selected.label, false);
                    refreshThemeDropdown(savedTheme);
                });


        themesAutoComplete.setOnItemClickListener((parent, view, position, id) -> {
            ThemeItem item = (ThemeItem) parent.getItemAtPosition(position);
            if (item.mode.equals(currentTheme)) {
                themesAutoComplete.dismissDropDown();
                return;
            }
            getSharedPreferences("settings", MODE_PRIVATE).edit()
                    .putBoolean("restore_settings", isSettingsOpen).apply();
            saveTheme(item.mode);
            currentTheme = item.mode;
            themesAutoComplete.dismissDropDown();
            applyTheme(item.mode);
            recreate();
        });

        themesAutoComplete.setOnClickListener(v -> bounceView(themeLayout));

        themeLayout.setEndIconOnClickListener(null);
        themeLayout.setEndIconVisible(false);



        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(getLocaleFromCode(selectedLanguageCode));

                tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {

                    }

                    @Override
                    public void onDone(String utteranceId) {
                        if ("sentenceUtterance".equals(utteranceId)) {
                            runOnUiThread(() -> {
                                playAudio = false;


                                if (isPreviousTssCurrentLetterOn){
                                    muteLetterBtn.setImageResource(R.drawable.volume_on);
                                    bounceImageButton(muteLetterBtn);
                                    ttsCurrentLetter = true;
                                }
                                muteLetterBtn.setEnabled(true);

                                bounceImageButton(playBtn);
                                playBtn.setImageResource(R.drawable.play);
                            });
                        }
                    }

                    @Override
                    public void onError(String utteranceId) {}
                });
            }
        });

        View.OnTouchListener swipeListener = createSwipeListener();
        findViewById(R.id.cardHeader).setOnTouchListener(swipeListener);
        findViewById(R.id.settingsHeader).setOnTouchListener(swipeListener);
        findViewById(R.id.helpHeader).setOnTouchListener(swipeListener);



        expandHitArea(muteLetterBtn);
        expandHitArea(flipBtn);
        expandHitArea(playBtn);
        expandHitArea(settingsBtn);

        cameraExecutor = Executors.newSingleThreadExecutor();

        recordBtn.post(() -> {

            float containerHeight = recordContainer.getHeight();
            float buttonHeight = recordBtn.getHeight();

            float containerCenterY = containerHeight / 2f;
            float buttonCenterY = recordBtn.getTop() + buttonHeight / 2f;

            
            float rawCenterOffset = containerCenterY - buttonCenterY;

            
            centerVisualOffset = rawCenterOffset
                    - recordContainer.getHeight() * 0.04f;

            
            bottomOffset = centerVisualOffset + recordContainer.getHeight() * 0.39f;

            
            Paint paint = recordBtn.getPaint();
            int padding = recordBtn.getPaddingLeft() + recordBtn.getPaddingRight();

            widthRecord = (int) Math.ceil(
                    paint.measureText(getString(R.string.press_to_record_letters)) + padding
            );

            widthStop = (int) Math.ceil(
                    paint.measureText(getString(R.string.press_to_stop)) + padding
            );

            
            recordBtn.setTranslationY(centerVisualOffset);

            hasMovedToBottom = false;
        });

        recordBtn.setOnClickListener(v -> {

            isRecording = !isRecording;
            boolean expanding = isRecording;

            int targetWidth = expanding ? widthStop : widthRecord;
            animateButtonWidth(recordBtn, targetWidth);

            Context localeContext = LocaleHelper.wrap(this, getLocaleFromCode(selectedLanguageCode));

            if (expanding) {
                recordBtn.setText(localeContext.getString(R.string.press_to_stop));
            } else {
                recordBtn.setText(localeContext.getString(R.string.press_to_record_letters));
            }

            ObjectAnimator move = null;

            if (!hasMovedToBottom) {
                float startY = centerVisualOffset;
                float endY = bottomOffset;
                float overshoot = 6f;

                move = ObjectAnimator.ofFloat(
                        recordBtn,
                        "translationY",
                        startY,
                        endY + overshoot,
                        endY
                );

                move.setDuration(1500);
                move.setInterpolator(
                        new PathInterpolator(0.20f, 0.90f, 0.28f, 1.15f)
                );

                hasMovedToBottom = true;
            }

            if (move != null) {
                move.start();
            }else if(tvSentence.getText().toString().isEmpty() && hasMovedToBottom && !isRecording){
                resetRecordButtonToCenter(recordBtn);
            }

            bounceView(recordBtn);
        });

        muteLetterBtn.setOnClickListener(v -> {
            ttsCurrentLetter = !ttsCurrentLetter;

            
            if (ttsCurrentLetter) {
                isPreviousTssCurrentLetterOn = true;
                muteLetterBtn.setImageResource(R.drawable.volume_on);
            } else {
                muteLetterBtn.setImageResource(R.drawable.volume_off);
                isPreviousTssCurrentLetterOn = false;
            }

            bounceImageButton(muteLetterBtn);

        });

        playBtn.setOnClickListener(v -> {

            String sentence = tvSentence.getText().toString();
            if (sentence.isEmpty()) return;

            if (!playAudio) {

                
                tts.stop();



                if (ttsCurrentLetter){
                    muteLetterBtn.setImageResource(R.drawable.volume_off);
                    muteLetterBtn.setEnabled(false);
                    ttsCurrentLetter = false;
                    bounceView(muteLetterBtn);
                }

                playAudio = true;





                playBtn.setImageResource(R.drawable.pause);

                tts.speak(
                        sentence,
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "sentenceUtterance"
                );

            } else {

                
                tts.stop();

                playAudio = false;
                ttsCurrentLetter = true;
                muteLetterBtn.setEnabled(true);

                playBtn.setImageResource(R.drawable.play);
            }

            bounceImageButton(playBtn);
        });

        flipBtn.setOnClickListener(v -> {

            float startRotation = flipBtn.getRotation();
            float endRotation = startRotation + 180f;

            
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(
                    flipBtn, "scaleX",
                    1f, 1.14f, 0.98f, 1f
            );

            ObjectAnimator scaleY = ObjectAnimator.ofFloat(
                    flipBtn, "scaleY",
                    1f, 1.14f, 0.98f, 1f
            );

            scaleX.setDuration(900);
            scaleY.setDuration(900);

            PathInterpolator scaleInterpolator =
                    new PathInterpolator(0.20f, 1.20f, 0.32f, 1f);

            scaleX.setInterpolator(scaleInterpolator);
            scaleY.setInterpolator(scaleInterpolator);

            
            ObjectAnimator rotation = ObjectAnimator.ofFloat(
                    flipBtn,
                    "rotation",
                    startRotation,
                    endRotation + 10f,
                    endRotation
            );

            rotation.setDuration(1000);

            rotation.setInterpolator(
                    new PathInterpolator(0.22f, 0.85f, 0.28f, 1f)
            );

            
            rotation.addUpdateListener(anim -> {
                float progress = anim.getAnimatedFraction();
                if (progress > 0.47f && progress < 0.53f) {
                    if (flipBtn.getTag() == null) {
                        flipBtn.setTag("switched");
                        useFrontCamera = !useFrontCamera;
                        overlayView.setMirror(useFrontCamera);
                        restartCamera();
                    }
                }
            });

            rotation.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    flipBtn.setTag(null);
                }
            });

            AnimatorSet set = new AnimatorSet();
            set.playTogether(scaleX, scaleY, rotation);
            set.start();
        });

        settingsBtn.setOnClickListener(v -> {
            float cardWidth = mainContent.getWidth();
            PathInterpolator bounceInterpolator = new PathInterpolator(0.34f, 1.56f, 0.64f, 1f);

            
            if (isHelpOpen) {
                ObjectAnimator a1 = ObjectAnimator.ofFloat(helpContent, "translationX", 0f, -cardWidth);
                ObjectAnimator a2 = ObjectAnimator.ofFloat(mainContent, "translationX", cardWidth, -cardWidth);
                ObjectAnimator a3 = ObjectAnimator.ofFloat(settingsContent, "translationX", cardWidth, 0f);
                a1.setDuration(500); a2.setDuration(500); a3.setDuration(500);
                a1.setInterpolator(bounceInterpolator); a2.setInterpolator(bounceInterpolator); a3.setInterpolator(bounceInterpolator);
                AnimatorSet set = new AnimatorSet();
                set.playTogether(a1, a2, a3);
                set.start();
                isHelpOpen = false;
                isSettingsOpen = true;
                bounceImageButton(helpBtn);
                triggerSettingsIconOpen();
                return;
            }

            if (!isSettingsOpen) {
                ObjectAnimator a1 = ObjectAnimator.ofFloat(mainContent, "translationX", 0f, -cardWidth);
                ObjectAnimator a2 = ObjectAnimator.ofFloat(settingsContent, "translationX", cardWidth, 0f);
                a1.setDuration(500); a2.setDuration(500);
                a1.setInterpolator(bounceInterpolator); a2.setInterpolator(bounceInterpolator);
                AnimatorSet set = new AnimatorSet();
                set.playTogether(a1, a2);
                set.start();
                isSettingsOpen = true;
                triggerSettingsIconOpen();
            } else {
                ObjectAnimator a1 = ObjectAnimator.ofFloat(settingsContent, "translationX", 0f, cardWidth);
                ObjectAnimator a2 = ObjectAnimator.ofFloat(mainContent, "translationX", -cardWidth, 0f);
                a1.setDuration(500); a2.setDuration(500);
                a1.setInterpolator(bounceInterpolator); a2.setInterpolator(bounceInterpolator);
                AnimatorSet set = new AnimatorSet();
                set.playTogether(a1, a2);
                set.start();
                isSettingsOpen = false;
                triggerSettingsIconClose();
            }
        });

        helpBtn.setOnClickListener(v -> {
            float cardWidth = mainContent.getWidth();
            PathInterpolator bounceInterpolator = new PathInterpolator(0.34f, 1.56f, 0.64f, 1f);

            
            if (isSettingsOpen) {
                ObjectAnimator a1 = ObjectAnimator.ofFloat(settingsContent, "translationX", 0f, cardWidth);
                ObjectAnimator a2 = ObjectAnimator.ofFloat(mainContent, "translationX", -cardWidth, cardWidth);
                ObjectAnimator a3 = ObjectAnimator.ofFloat(helpContent, "translationX", -cardWidth, 0f);
                a1.setDuration(500); a2.setDuration(500); a3.setDuration(500);
                a1.setInterpolator(bounceInterpolator); a2.setInterpolator(bounceInterpolator); a3.setInterpolator(bounceInterpolator);
                AnimatorSet set = new AnimatorSet();
                set.playTogether(a1, a2, a3);
                set.start();
                isSettingsOpen = false;
                isHelpOpen = true;
                triggerSettingsIconClose();
                bounceImageButton(helpBtn);
                return;
            }

            if (!isHelpOpen) {
                ObjectAnimator a1 = ObjectAnimator.ofFloat(mainContent, "translationX", 0f, cardWidth);
                ObjectAnimator a2 = ObjectAnimator.ofFloat(helpContent, "translationX", -cardWidth, 0f);
                a1.setDuration(500); a2.setDuration(500);
                a1.setInterpolator(bounceInterpolator); a2.setInterpolator(bounceInterpolator);
                AnimatorSet set = new AnimatorSet();
                set.playTogether(a1, a2);
                set.start();
                isHelpOpen = true;

            } else {
                ObjectAnimator a1 = ObjectAnimator.ofFloat(helpContent, "translationX", 0f, -cardWidth);
                ObjectAnimator a2 = ObjectAnimator.ofFloat(mainContent, "translationX", cardWidth, 0f);
                a1.setDuration(500); a2.setDuration(500);
                a1.setInterpolator(bounceInterpolator); a2.setInterpolator(bounceInterpolator);
                AnimatorSet set = new AnimatorSet();
                set.playTogether(a1, a2);
                set.start();
                isHelpOpen = false;
            }
            bounceImageButton(helpBtn);
        });

        clearBtn.setOnClickListener(v -> {

            tvSentence.setText("");

            shrinkImageButton(clearBtn);
            shrinkImageButton(playBtn);


        });

        autoCompleteTextView.setOnItemClickListener((parent, view, position, id) -> {
            LanguageItem item = (LanguageItem) parent.getItemAtPosition(position);

            
            if (item.code.equals(selectedLanguageCode)) {
                autoCompleteTextView.dismissDropDown();
                return;
            }

            selectedLanguageCode = item.code;

            
            if (tts != null) {
                tts.stop();
            }

            
            autoCompleteTextView.dismissDropDown();

            
            saveLanguage(new Locale(item.code));

            
            if (tts != null) {
                tts.setLanguage(getLocaleFromCode(selectedLanguageCode));
            }

            
            updateUILanguage();


            
            bounceView(languageLayout);
        });

        autoCompleteTextView.setOnClickListener(v -> bounceView(languageLayout));



        if (hasCameraPermission()) {
            setupHandLandmarker();
            loadTFLiteModel();
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION
            );
        }

        View decorView = getWindow().getDecorView();
        decorView.setOnApplyWindowInsetsListener((v, insets) -> {
            int left = insets.getSystemWindowInsetLeft();
            int top = insets.getSystemWindowInsetTop();
            int bottom = insets.getSystemWindowInsetBottom();
            int right = insets.getSystemWindowInsetRight();
            v.setPadding(left, top, right, bottom);
            return insets.consumeSystemWindowInsets();
        });

        boolean restoreSettings = getSharedPreferences("settings", MODE_PRIVATE)
                .getBoolean("restore_settings", false);
        if (restoreSettings) {
            getSharedPreferences("settings", MODE_PRIVATE).edit()
                    .putBoolean("restore_settings", false).apply();
            
            mainContent.setTranslationX(-10000f);
            settingsContent.setTranslationX(0f);
            isSettingsOpen = true;
            settingsBtn.setRotation(-90f);
            
            mainContent.post(() -> mainContent.setTranslationX(-mainContent.getWidth()));
        }
    }

    private View.OnTouchListener createSwipeListener() {
        return (v, event) -> {
            v.performClick();
            float cardWidth = mainContent.getWidth();
            float threshold = cardWidth * (SWIPE_THRESHOLD_PERCENT / 100f);

            ImageButton helpBtn = findViewById(R.id.help);

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    swipeTouchStartX = event.getX();
                    return true;

                case MotionEvent.ACTION_MOVE: {
                    float deltaX = event.getX() - swipeTouchStartX;
                    if (isHelpOpen) {
                        float clamped = Math.min(0f, deltaX); 
                        helpContent.setTranslationX(clamped - cardWidth + cardWidth); 
                        
                        float drag = Math.max(0f, deltaX);
                        helpContent.setTranslationX(-drag == 0 ? 0f : -cardWidth + drag > 0 ? 0 : drag - cardWidth);
                        helpContent.setTranslationX(Math.min(0f, -cardWidth + (cardWidth + deltaX)));
                        mainContent.setTranslationX(cardWidth + Math.min(0f, deltaX));
                    } else if (!isSettingsOpen) {
                        float clampedLeft = Math.min(0f, deltaX);
                        float clampedRight = Math.max(0f, deltaX);
                        mainContent.setTranslationX(deltaX < 0 ? clampedLeft : clampedRight);
                        settingsContent.setTranslationX(cardWidth + clampedLeft);
                        helpContent.setTranslationX(-cardWidth + clampedRight);
                    } else {
                        float clamped = Math.max(0f, deltaX);
                        mainContent.setTranslationX(-cardWidth + clamped);
                        settingsContent.setTranslationX(clamped);
                    }
                    return true;
                }

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    v.performClick();
                    float deltaX = event.getX() - swipeTouchStartX;
                    PathInterpolator bounceInterpolator = new PathInterpolator(0.34f, 1.56f, 0.64f, 1f);

                    if (isHelpOpen) {
                        
                        if (deltaX < -threshold) {
                            AnimatorSet set = new AnimatorSet();
                            ObjectAnimator a1 = ObjectAnimator.ofFloat(helpContent, "translationX", helpContent.getTranslationX(), -cardWidth);
                            ObjectAnimator a2 = ObjectAnimator.ofFloat(mainContent, "translationX", mainContent.getTranslationX(), 0f);
                            a1.setDuration(350); a2.setDuration(350);
                            a1.setInterpolator(bounceInterpolator); a2.setInterpolator(bounceInterpolator);
                            set.playTogether(a1, a2); set.start();
                            isHelpOpen = false;
                            bounceImageButton(helpBtn);
                        } else {
                            
                            AnimatorSet set = new AnimatorSet();
                            ObjectAnimator a1 = ObjectAnimator.ofFloat(helpContent, "translationX", helpContent.getTranslationX(), 0f);
                            ObjectAnimator a2 = ObjectAnimator.ofFloat(mainContent, "translationX", mainContent.getTranslationX(), cardWidth);
                            a1.setDuration(350); a2.setDuration(350);
                            a1.setInterpolator(bounceInterpolator); a2.setInterpolator(bounceInterpolator);
                            set.playTogether(a1, a2); set.start();
                        }
                    } else if (!isSettingsOpen && deltaX < -threshold) {
                        AnimatorSet set = new AnimatorSet();
                        ObjectAnimator a1 = ObjectAnimator.ofFloat(mainContent, "translationX", mainContent.getTranslationX(), -cardWidth);
                        ObjectAnimator a2 = ObjectAnimator.ofFloat(settingsContent, "translationX", settingsContent.getTranslationX(), 0f);
                        a1.setDuration(350); a2.setDuration(350);
                        a1.setInterpolator(bounceInterpolator); a2.setInterpolator(bounceInterpolator);
                        set.playTogether(a1, a2); set.start();
                        isSettingsOpen = true;
                        triggerSettingsIconOpen();

                    } else if (!isSettingsOpen && deltaX > threshold) {
                        
                        AnimatorSet set = new AnimatorSet();
                        ObjectAnimator a1 = ObjectAnimator.ofFloat(mainContent, "translationX", mainContent.getTranslationX(), cardWidth);
                        ObjectAnimator a2 = ObjectAnimator.ofFloat(helpContent, "translationX", helpContent.getTranslationX(), 0f);
                        a1.setDuration(350); a2.setDuration(350);
                        a1.setInterpolator(bounceInterpolator); a2.setInterpolator(bounceInterpolator);
                        set.playTogether(a1, a2); set.start();
                        isHelpOpen = true;
                        bounceImageButton(helpBtn);

                    } else if (!isSettingsOpen) {
                        
                        AnimatorSet set = new AnimatorSet();
                        ObjectAnimator a1 = ObjectAnimator.ofFloat(mainContent, "translationX", mainContent.getTranslationX(), 0f);
                        ObjectAnimator a2 = ObjectAnimator.ofFloat(settingsContent, "translationX", settingsContent.getTranslationX(), cardWidth);
                        a1.setDuration(350); a2.setDuration(350);
                        a1.setInterpolator(bounceInterpolator); a2.setInterpolator(bounceInterpolator);
                        set.playTogether(a1, a2); set.start();

                    } else if (deltaX > threshold) {
                        AnimatorSet set = new AnimatorSet();
                        ObjectAnimator a1 = ObjectAnimator.ofFloat(settingsContent, "translationX", settingsContent.getTranslationX(), cardWidth);
                        ObjectAnimator a2 = ObjectAnimator.ofFloat(mainContent, "translationX", mainContent.getTranslationX(), 0f);
                        a1.setDuration(350); a2.setDuration(350);
                        a1.setInterpolator(bounceInterpolator); a2.setInterpolator(bounceInterpolator);
                        set.playTogether(a1, a2); set.start();
                        isSettingsOpen = false;
                        triggerSettingsIconClose();

                    } else {
                        
                        AnimatorSet set = new AnimatorSet();
                        ObjectAnimator a1 = ObjectAnimator.ofFloat(settingsContent, "translationX", settingsContent.getTranslationX(), 0f);
                        ObjectAnimator a2 = ObjectAnimator.ofFloat(mainContent, "translationX", mainContent.getTranslationX(), -cardWidth);
                        a1.setDuration(350); a2.setDuration(350);
                        a1.setInterpolator(bounceInterpolator); a2.setInterpolator(bounceInterpolator);
                        set.playTogether(a1, a2); set.start();
                    }
                    return true;
                }
            }
            return false;
        };
    }

    private void triggerSettingsIconOpen() {
        bounceImageButton(settingsBtn);
        ObjectAnimator rotation = ObjectAnimator.ofFloat(settingsBtn, "rotation", settingsBtn.getRotation(), -100f, -90f);
        rotation.setDuration(800);
        rotation.setInterpolator(new PathInterpolator(0.34f, 1.56f, 0.64f, 1f));
        rotation.start();
    }

    private void triggerSettingsIconClose() {
        bounceImageButton(settingsBtn);
        ObjectAnimator rotation = ObjectAnimator.ofFloat(settingsBtn, "rotation", settingsBtn.getRotation(), 10f, 0f);
        rotation.setDuration(800);
        rotation.setInterpolator(new PathInterpolator(0.34f, 1.56f, 0.64f, 1f));
        rotation.start();
    }

    private static class ThemeItem {
        final String mode; 
        final String label;

        ThemeItem(String mode, String label) {
            this.mode = mode;
            this.label = label;
        }

        @NonNull
        @Override
        public String toString() {
            return label;
        }
    }

    private void saveTheme(String mode) {
        getSharedPreferences("settings", MODE_PRIVATE)
                .edit()
                .putString("theme", mode)
                .apply();
    }

    private String loadSavedTheme() {
        return getSharedPreferences("settings", MODE_PRIVATE)
                .getString("theme", "system");
    }

    private void applyTheme(String mode) {
        switch (mode) {
            case "dark":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case "light":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    private void refreshThemeDropdown(String selectedMode) {
        themeAdapter.clear();
        for (ThemeItem item : allThemes) {
            if (!item.mode.equals(selectedMode)) {
                themeAdapter.add(item);
            }
        }
        themeAdapter.notifyDataSetChanged();
    }

    private void resetRecordButtonToCenter( Button recordBtn) {
        isRecording = false;
        hasMovedToBottom = false;

        Context localeContext = LocaleHelper.wrap(this, getLocaleFromCode(selectedLanguageCode));

        
        recordBtn.setText(localeContext.getString(R.string.press_to_record_letters));

        
        animateButtonWidth(recordBtn, widthRecord);

        
        ObjectAnimator moveBack = ObjectAnimator.ofFloat(
                recordBtn,
                "translationY",
                recordBtn.getTranslationY(),
                centerVisualOffset - 12f,
                centerVisualOffset
        );

        moveBack.setDuration(1500);
        moveBack.setInterpolator(
                new PathInterpolator(0.20f, 0.90f, 0.28f, 1.15f)
        );

        moveBack.start();
        bounceView(recordBtn);
    }

    private void bounceView(View view) {
        if (view == null) return;

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(
                view, "scaleX", 1f, 0.85f, 1f
        );
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(
                view, "scaleY", 1f, 0.85f, 1f
        );

        scaleX.setInterpolator(IOS_SPRING);
        scaleY.setInterpolator(IOS_SPRING);

        scaleX.setDuration(500);
        scaleY.setDuration(500);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.start();
    }

    private void loadTFLiteModel() {
        try {
            tflite = new Interpreter(loadModelFile());
        } catch (Exception e) {
            Log.e("MainActivity", "Failed to load TFLite model", e);
        }
    }

    private String predictLetter(float[] normalizedLandmarks) {
        if (tflite == null) return "-";

        float[][] input = new float[1][63];
        input[0] = normalizedLandmarks;
        float[][] output = new float[1][numLetters];
        tflite.run(input, output);

        int maxIdx = 0;
        float maxProb = output[0][0];
        for (int i = 1; i < numLetters; i++) {
            if (output[0][i] > maxProb) {
                maxProb = output[0][i];
                maxIdx = i;
            }
        }

        
        if (maxProb < CONFIDENCE_THRESHOLD) {
            return "-";
        }

        String rawPrediction = String.valueOf(letters[maxIdx]);
        return getSmoothedPrediction(rawPrediction, maxProb);
    }

    private String getSmoothedPrediction(String currentPrediction, float confidence) {
        predictionHistory.add(currentPrediction);

        if (predictionHistory.size() > SMOOTHING_WINDOW) {
            predictionHistory.removeFirst();
        }

        if (predictionHistory.size() < 3) {
            return "-";
        }

        
        smoothingCounts.clear();
        for (String pred : predictionHistory) {
            smoothingCounts.merge(pred, 1, Integer::sum);
        }

        String mostCommon = "-";
        int maxCount = 0;
        for (java.util.Map.Entry<String, Integer> entry : smoothingCounts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                mostCommon = entry.getKey();
            }
        }

        if (maxCount >= MIN_STABLE_PREDICTIONS && confidence > CONFIDENCE_THRESHOLD) {
            return mostCommon;
        }

        return "-";
    }

    private void clearPredictionHistory() {
        predictionHistory.clear();
    }
    private MappedByteBuffer loadModelFile() throws Exception {
        try (AssetFileDescriptor afd = getAssets().openFd(MainActivity.MODEL_NAME);
             FileInputStream fis = new FileInputStream(afd.getFileDescriptor());
             FileChannel fileChannel = fis.getChannel()) {

            return fileChannel.map(
                    FileChannel.MapMode.READ_ONLY,
                    afd.getStartOffset(),
                    afd.getDeclaredLength()
            );
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupHandLandmarker();
            loadTFLiteModel();
            startCamera();
        }
    }

    private void animateButtonWidth(Button button, int targetWidth) {
        int startWidth = button.getWidth();

        
        if (startWidth == targetWidth) {
            return;
        }

        ValueAnimator animator = ValueAnimator.ofInt(startWidth, targetWidth);

        animator.addUpdateListener(animation -> {
            ViewGroup.LayoutParams params = button.getLayoutParams();
            params.width = (int) animation.getAnimatedValue();
            button.setLayoutParams(params);
        });

        
        animator.setDuration(600);
        animator.setInterpolator(IOS_SPRING);
        animator.start();
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        Locale locale = getLocaleFromCode(
                newBase
                        .getSharedPreferences("settings", MODE_PRIVATE)
                        .getString("lang", "en")
        );

        Context context = LocaleHelper.wrap(newBase, locale);
        super.attachBaseContext(context);
    }

    public static final class LocaleHelper {

        public static Context wrap(Context context, Locale locale) {
            Locale.setDefault(locale);

            Configuration config = new Configuration(context.getResources().getConfiguration());
            config.setLocale(locale);

            return context.createConfigurationContext(config);
        }
    }

    private void restartCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            startCamera();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(cameraExecutor, this::analyzeImageForLiveStream);
                CameraSelector cameraSelector =
                        new CameraSelector.Builder()
                                .requireLensFacing(
                                        useFrontCamera
                                                ? CameraSelector.LENS_FACING_FRONT
                                                : CameraSelector.LENS_FACING_BACK
                                )
                                .build();
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        analysis
                );
            } catch (Exception e) {
                Log.e("MainActivity", "Failed to load TFLite model", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }


    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeImageForLiveStream(@NonNull ImageProxy imageProxy) {
        
        if (isRecreating || isFinishing() || isDestroyed()) {
            imageProxy.close();
            return;
        }

        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        if (yuvConverter == null) {
            yuvConverter = new YuvToRgbConverter();
        }

        Bitmap bitmap = yuvConverter.convert(imageProxy.getImage());
        Bitmap rotatedBitmap = bitmap;

        int rotation = imageProxy.getImageInfo().getRotationDegrees();
        if (rotation != 0) {
            rotationMatrix.reset();  
            rotationMatrix.postRotate(rotation);
            rotatedBitmap = Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.getWidth(),
                    bitmap.getHeight(),
                    rotationMatrix,  
                    true
            );
            
            if (rotatedBitmap != bitmap) {
                bitmap.recycle();
            }
        }

        MPImage mpImage = new BitmapImageBuilder(rotatedBitmap).build();
        long timestamp = System.currentTimeMillis();


        if (!isRecreating && !isFinishing() && !isDestroyed() && handLandmarker != null) {
            handLandmarker.detectAsync(mpImage, timestamp);
        }


        rotatedBitmap.recycle();

        imageProxy.close();
    }

    private void setupHandLandmarker() {
        @SuppressLint("SetTextI18n") HandLandmarker.HandLandmarkerOptions options =
                HandLandmarker.HandLandmarkerOptions.builder()
                        .setBaseOptions(
                                BaseOptions.builder()
                                        .setModelAssetPath("hand_landmarker.task")
                                        .build()
                        )
                        .setRunningMode(RunningMode.LIVE_STREAM)
                        .setNumHands(1)
                        .setMinHandDetectionConfidence(0.7f)
                        .setMinTrackingConfidence(0.7f)
                        .setMinHandPresenceConfidence(0.6f)
                        .setResultListener(
                                (result, image) -> {
                                    
                                    if (isRecreating || isFinishing() || isDestroyed()) {
                                        return;
                                    }

                                    runOnUiThread(() -> {
                                        
                                        if (isRecreating || isFinishing() || isDestroyed()) {
                                            return;
                                        }

                                        if (!result.landmarks().isEmpty()) {

                                            
                                            overlayView.setResults(
                                                    result.landmarks(),
                                                    image.getHeight(),
                                                    image.getWidth()
                                            );

                                            List<NormalizedLandmark> hand = result.landmarks().get(0);


                                            boolean isLeftHand = isIsLeftHand(result);


                                            float[] normalized = normalizeLandmarks(hand, isLeftHand);

                                            String predictedLetter = predictLetter(normalized);


                                            if (predictedLetter.equals(" ")){
                                                tvLetter.setText(cachedLocaleContext.getString(R.string.space));
                                            }else if(predictedLetter.equals("~")){
                                                tvLetter.setText(cachedLocaleContext.getString(R.string.delete));
                                            }else {
                                                tvLetter.setText(predictedLetter);
                                            }


                                            long now = System.currentTimeMillis();

                                            if (!predictedLetter.equals(currentLetter)) {
                                                currentLetter = predictedLetter;
                                                letterStartTime = now;
                                                lastSpeakTime = 0;
                                            } else {
                                                if (now - letterStartTime >= minDisplayTimeMs) {
                                                    if (lastSpeakTime == 0 || now - lastSpeakTime >= speakIntervalMs) {
                                                        if(ttsCurrentLetter && !playAudio && tts != null && !currentLetter.equals("-")){
                                                            if(currentLetter.equals(" ")){
                                                                tts.speak(cachedLocaleContext.getString(R.string.space), TextToSpeech.QUEUE_FLUSH, null, "letterUtterance");
                                                            } else if (currentLetter.equals("~")) {
                                                                tts.speak(cachedLocaleContext.getString(R.string.delete), TextToSpeech.QUEUE_FLUSH, null, "letterUtterance");
                                                            } else{
                                                                tts.speak(currentLetter, TextToSpeech.QUEUE_FLUSH, null, "letterUtterance");
                                                            }

                                                        }
                                                        if (isRecording && !currentLetter.equals("-")) {

                                                            
                                                            boolean isDifferentLetter = !currentLetter.equals(lastRecordedLetter);
                                                            boolean enoughTimePassed = (now - lastRecordTime) >= speakIntervalMs;

                                                            if (isDifferentLetter || enoughTimePassed) {
                                                                String text = tvSentence.getText().toString();

                                                                if (text.isEmpty() && !currentLetter.equals("~")) {
                                                                    clearBtn.setVisibility(View.VISIBLE);
                                                                    growImageButton(clearBtn);
                                                                    playBtn.setVisibility(View.VISIBLE);
                                                                    growImageButton(playBtn);
                                                                }

                                                                if(currentLetter.equals("~")){
                                                                    if (!text.isEmpty()) {
                                                                        String newText = text.substring(0, text.length() - 1);
                                                                        tvSentence.setText(capitalizeFirstLetter(newText.toLowerCase()));

                                                                        
                                                                        if (newText.isEmpty()) {
                                                                            shrinkImageButton(clearBtn);
                                                                            shrinkImageButton(playBtn);
                                                                        }
                                                                    }


                                                                }else {
                                                                    tvSentence.setText(capitalizeFirstLetter((text + currentLetter).toLowerCase()));
                                                                }


                                                                lastRecordedLetter = currentLetter;
                                                                lastRecordTime = now;
                                                            }
                                                        }
                                                        lastSpeakTime = now;
                                                    }
                                                }
                                            }
                                        } else {
                                            overlayView.clear();
                                            if (!"-".equals(tvLetter.getText().toString())) {
                                                tvLetter.setText("-");
                                            }
                                            currentLetter = "";
                                            letterStartTime = 0;
                                            lastSpeakTime = 0;
                                            clearPredictionHistory();
                                        }
                                    });
                                })
                        .build();
        handLandmarker = HandLandmarker.createFromOptions(this, options);
    }

    private static boolean isIsLeftHand(HandLandmarkerResult result) {
        boolean isLeftHand = false;

        if (!result.handedness().isEmpty()
                && !result.handedness().get(0).isEmpty()) {

            String handednessLabel =
                    result.handedness().get(0).get(0).categoryName();

            isLeftHand = handednessLabel.equals("Left");
        }
        return isLeftHand;
    }


    private void expandHitArea(View target) {
        View parent = (View) target.getParent();

        parent.post(() -> {
            Rect rect = new Rect();
            target.getHitRect(rect);

            rect.top -= MainActivity.HIT_AREA_PADDING_PX;
            rect.bottom += MainActivity.HIT_AREA_PADDING_PX;
            rect.left -= MainActivity.HIT_AREA_PADDING_PX;
            rect.right += MainActivity.HIT_AREA_PADDING_PX;

            TouchDelegate delegate = new TouchDelegate(rect, target);

            TouchDelegate current = parent.getTouchDelegate();
            if (current instanceof MultiTouchDelegate) {
                ((MultiTouchDelegate) current).addDelegate(delegate);
            } else {
                MultiTouchDelegate multi = new MultiTouchDelegate(parent);
                multi.addDelegate(delegate);
                parent.setTouchDelegate(multi);
            }
        });
    }

    private Locale getLocaleFromCode(String code) {
        return "id".equals(code)
                ? new Locale("id")
                : Locale.ENGLISH;
    }

    private void updateUILanguage() {
        cachedLocaleContext = LocaleHelper.wrap(this, getLocaleFromCode(selectedLanguageCode));

        
        Context newContext = LocaleHelper.wrap(this, getLocaleFromCode(selectedLanguageCode));

        
        TextView currentLetterLabel = findViewById(R.id.currentLetter);
        if (currentLetterLabel != null) {
            currentLetterLabel.setText(newContext.getString(R.string.current_letter));
        }

        TextView settingsLabel = findViewById(R.id.settingsLabel);
        if (settingsLabel != null) {
            settingsLabel.setText(newContext.getString(R.string.settings));
        }

        TextView themeLabel = findViewById(R.id.themeLabel);
        if (themeLabel != null) {
            themeLabel.setText(newContext.getString(R.string.theme));
        }

        TextView holdDuration = findViewById(R.id.holdDuration);
        if (holdDuration != null) {
            holdDuration.setText(newContext.getString(R.string.holdduration));
        }

        TextView speechDelay = findViewById(R.id.speechDelay);
        if (speechDelay != null) {
            speechDelay.setText(newContext.getString(R.string.speechdelay));
        }

        TextView ms = findViewById(R.id.ms);
        if (ms != null) {
            ms.setText(newContext.getString(R.string.millisecond));
        }

        TextView ms2 = findViewById(R.id.ms2);
        if (ms2 != null) {
            ms2.setText(newContext.getString(R.string.millisecond));
        }

        TextView showSkeleton = findViewById(R.id.showSkeleton);
        if (showSkeleton != null) {
            showSkeleton.setText(newContext.getString(R.string.showSkeleton));
        }

        TextView help = findViewById(R.id.tvHelp);
        if (help != null) {
            help.setText(newContext.getString(R.string.help));
        }

        TextView themes = findViewById(R.id.themes);
        if (themes != null) {
            switch (currentTheme) {
                case "dark":
                    themes.setText(newContext.getString(R.string.darkmode));
                    break;
                case "light":
                    themes.setText(newContext.getString(R.string.lightmode));
                    break;
                case "system":
                    themes.setText(newContext.getString(R.string.systemdefault));
                    break;
            }

            allThemes.clear();
            allThemes.add(new ThemeItem("dark", newContext.getString(R.string.darkmode)));
            allThemes.add(new ThemeItem("light", newContext.getString(R.string.lightmode)));
            allThemes.add(new ThemeItem("system", newContext.getString(R.string.systemdefault)));
            refreshThemeDropdown(currentTheme);

        }


        
        Button recordBtn = findViewById(R.id.record);
        if (recordBtn != null) {
            
            Paint paint = recordBtn.getPaint();
            int padding = recordBtn.getPaddingLeft() + recordBtn.getPaddingRight();

            widthRecord = (int) Math.ceil(
                    paint.measureText(newContext.getString(R.string.press_to_record_letters)) + padding
            );

            widthStop = (int) Math.ceil(
                    paint.measureText(newContext.getString(R.string.press_to_stop)) + padding
            );

            
            if (isRecording) {
                recordBtn.setText(newContext.getString(R.string.press_to_stop));
                animateButtonWidth(recordBtn, widthStop);
            } else {
                recordBtn.setText(newContext.getString(R.string.press_to_record_letters));
                animateButtonWidth(recordBtn, widthRecord);
            }
        }

        allLanguages.clear();
        allLanguages.add(new LanguageItem("en", newContext.getString(R.string.lang_english)));
        allLanguages.add(new LanguageItem("id", newContext.getString(R.string.lang_indonesia)));

        refreshLanguageDropdown();

        allLanguages.stream()
                .filter(item -> item.code.equals(selectedLanguageCode))
                .findFirst()
                .ifPresent(selectedItem -> {
                    autoCompleteTextView.setText(selectedItem.label, false);
                    refreshLanguageDropdown();
                });
    }
    private void saveLanguage(Locale locale) {
        getSharedPreferences("settings", MODE_PRIVATE)
                .edit()
                .putString("lang", locale.getLanguage())
                .apply();
    }

    private String loadSavedLanguage() {
        return getSharedPreferences("settings", MODE_PRIVATE)
                .getString("lang", "en");
    }

    private void refreshLanguageDropdown() {
        languageAdapter.clear();

        
        for (LanguageItem item : allLanguages) {
            if (!item.code.equals(selectedLanguageCode)) {
                languageAdapter.add(item);
            }
        }

        languageAdapter.notifyDataSetChanged();
    }

    public static String capitalizeFirstLetter(String original) {
        if (original == null || original.isEmpty()) {
            return original;
        }
        
        return original.substring(0, 1).toUpperCase() + original.substring(1);
    }
    private float[] normalizeLandmarks(List<NormalizedLandmark> lm, boolean isLeftHand) {
        float[] out = new float[63];

        
        float baseX = lm.get(0).x();
        float baseY = lm.get(0).y();
        float baseZ = lm.get(0).z();

        
        float middleX = lm.get(12).x();
        float middleY = lm.get(12).y();
        float middleZ = lm.get(12).z();

        
        float dx = middleX - baseX;
        float dy = middleY - baseY;
        float dz = middleZ - baseZ;
        float scale = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        
        if (scale == 0) scale = 1f;

        int idx = 0;

        
        for (int i = 0; i < lm.size(); i++) {
            float nx = (lm.get(i).x() - baseX) / scale;
            float ny = (lm.get(i).y() - baseY) / scale;
            float nz = (lm.get(i).z() - baseZ) / scale;

            
            if (isLeftHand) {
                nx = -nx;
            }

            out[idx++] = nx;
            out[idx++] = ny;
            out[idx++] = nz;
        }

        return out;
    }

    public static class MultiTouchDelegate extends TouchDelegate {

        private final List<TouchDelegate> delegates = new ArrayList<>();

        public MultiTouchDelegate(View host) {
            super(new Rect(), host);
        }

        public void addDelegate(TouchDelegate delegate) {
            delegates.add(delegate);
        }

        @Override
        public boolean onTouchEvent(@NonNull MotionEvent event) {
            for (TouchDelegate delegate : delegates) {
                if (delegate.onTouchEvent(event)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static void growImageButton(ImageButton button) {


        ObjectAnimator scaleX = ObjectAnimator.ofFloat(
                button, "scaleX", 0f, 1.3f, 1f
        );
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(
                button, "scaleY", 0f, 1.3f, 1f
        );

        scaleX.setInterpolator(IOS_SPRING);
        scaleY.setInterpolator(IOS_SPRING);

        scaleX.setDuration(500);
        scaleY.setDuration(500);

        button.setClickable(true);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.start();
    }
    private static void shrinkImageButton(ImageButton button) {


        
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(button, "scaleX", 1f, 1.5f, 0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(button, "scaleY", 1f, 1.5f, 0f);

        scaleX.setInterpolator(IOS_SPRING);
        scaleY.setInterpolator(IOS_SPRING);

        scaleX.setDuration(500);
        scaleY.setDuration(500);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.start();

        button.setClickable(false);
    }

    private static void bounceImageButton(ImageButton button) {


        ObjectAnimator scaleX = ObjectAnimator.ofFloat(button, "scaleX", 1f, 0.85f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(button, "scaleY", 1f, 0.85f, 1f);

        scaleX.setInterpolator(IOS_SPRING);
        scaleY.setInterpolator(IOS_SPRING);

        scaleX.setDuration(500);
        scaleY.setDuration(500);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.start();
    }

    private static class LanguageItem {
        final String code;   
        final String label;  

        LanguageItem(String code, String label) {
            this.code = code;
            this.label = label;
        }

        @NonNull
        @Override
        public String toString() {
            return label; 
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onPause() {
        super.onPause();
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onDestroy() {
        
        isRecreating = true;

        super.onDestroy();

        
        if (cameraProvider != null) {
            try {
                cameraProvider.unbindAll();
            } catch (Exception e) {
                Log.e("MainActivity", "Error unbinding camera", e);
            }
        }

        
        if (cameraExecutor != null) {
            try {
                cameraExecutor.shutdownNow();
                boolean terminated = cameraExecutor.awaitTermination(500, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (!terminated) {
                    Log.w("MainActivity", "Camera executor did not terminate in time");
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Error shutting down executor", e);
            }
        }

        
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception e) {
                Log.e("MainActivity", "Error shutting down TTS", e);
            }
        }

        
        if (handLandmarker != null) {
            try {
                handLandmarker.close();
            } catch (Exception e) {
                Log.e("MainActivity", "Error closing hand landmark", e);
            }
        }

        
        if (tflite != null) {
            try {
                tflite.close();
            } catch (Exception e) {
                Log.e("MainActivity", "Error closing TFLite", e);
            }
        }
    }
}
