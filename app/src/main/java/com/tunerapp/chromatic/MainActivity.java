package com.tunerapp.chromatic;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity implements PitchDetector.Listener {

    private static final int MIC_PERMISSION_CODE = 100;
    private static final int A4_MIN = 400, A4_MAX = 480;
    private static final String PREFS_NAME = "tuner_settings";

    private TextView a4Value, noteText, noteSharp, noteOctave, freqReadout, centsReadout, statusMsg;
    private TextView profChromatic, profGusli;
    private View volFill;
    private CentsScaleView centsScale;

    private int a4 = 432;
    private String profile = "chromatic";

    private PitchDetector pitchDetector;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        a4 = prefs.getInt("a4", 432);
        profile = prefs.getString("profile", "chromatic");

        bindViews();
        updateA4Display();
        updateProfileButtons();
        setupListeners();

        pitchDetector = new PitchDetector(this, this);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, MIC_PERMISSION_CODE);
        } else {
            pitchDetector.start();
        }
    }

    private void bindViews() {
        a4Value = findViewById(R.id.a4Value);
        noteText = findViewById(R.id.noteText);
        noteSharp = findViewById(R.id.noteSharp);
        noteOctave = findViewById(R.id.noteOctave);
        freqReadout = findViewById(R.id.freqReadout);
        centsReadout = findViewById(R.id.centsReadout);
        statusMsg = findViewById(R.id.statusMsg);
        profChromatic = findViewById(R.id.profChromatic);
        profGusli = findViewById(R.id.profGusli);
        volFill = findViewById(R.id.volFill);
        centsScale = findViewById(R.id.centsScale);
    }

    private void setupListeners() {
        findViewById(R.id.plusBtn).setOnClickListener(v -> {
            if (a4 < A4_MAX) {
                a4++;
                updateA4Display();
                saveSettings();
            }
        });
        findViewById(R.id.minusBtn).setOnClickListener(v -> {
            if (a4 > A4_MIN) {
                a4--;
                updateA4Display();
                saveSettings();
            }
        });
        profChromatic.setOnClickListener(v -> {
            profile = "chromatic";
            updateProfileButtons();
            saveSettings();
        });
        profGusli.setOnClickListener(v -> {
            profile = "gusli";
            updateProfileButtons();
            saveSettings();
        });
    }

    private void updateA4Display() {
        a4Value.setText(a4 + " Hz");
    }

    private void updateProfileButtons() {
        boolean isChromatic = profile.equals("chromatic");
        profChromatic.setBackgroundResource(isChromatic ? R.drawable.card_bg_active : R.drawable.card_bg);
        profChromatic.setTextColor(isChromatic ? 0xFFFFFFFF : 0xFF8A8A8A);
        profGusli.setBackgroundResource(!isChromatic ? R.drawable.card_bg_active : R.drawable.card_bg);
        profGusli.setTextColor(!isChromatic ? 0xFFFFFFFF : 0xFF8A8A8A);
    }

    private void saveSettings() {
        prefs.edit().putInt("a4", a4).putString("profile", profile).apply();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MIC_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pitchDetector.start();
            } else {
                runOnUiThread(() -> statusMsg.setText("нет доступа к микрофону"));
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pitchDetector != null && pitchDetector.hasPermission()) {
            pitchDetector.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (pitchDetector != null) {
            pitchDetector.stop();
        }
    }

    @Override
    public void onPitchDetected(double frequency, double rms) {
        runOnUiThread(() -> {
            updateVolume(rms);

            TunerMath.NoteResult result = profile.equals("gusli")
                    ? TunerMath.nearestGusli(frequency, a4)
                    : TunerMath.nearestChromatic(frequency, a4);

            boolean sharp = result.name.contains("#");
            noteText.setText(result.name.replace("#", ""));
            noteSharp.setText(sharp ? "#" : "");
            noteOctave.setText(String.valueOf(result.octave));

            freqReadout.setText(String.format("%.1f Hz", frequency));
            centsReadout.setText(String.format("%s%.0f cents", result.cents >= 0 ? "+" : "", result.cents));

            centsScale.setCents((float) result.cents);

            statusMsg.setText("");
        });
    }

    @Override
    public void onSilence(double rms) {
        // keep the last detected note, frequency and cents on screen;
        // only the volume bar reacts to silence
        runOnUiThread(() -> updateVolume(rms));
    }

    private void updateVolume(double rms) {
        int pct = (int) Math.min(100, Math.max(0, rms * 390));
        FrameLayout parent = (FrameLayout) volFill.getParent();
        int totalHeight = parent.getHeight();
        if (totalHeight <= 0) return;
        int fillHeight = totalHeight * pct / 100;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) volFill.getLayoutParams();
        lp.height = fillHeight;
        volFill.setLayoutParams(lp);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pitchDetector != null) {
            pitchDetector.stop();
        }
    }
}
