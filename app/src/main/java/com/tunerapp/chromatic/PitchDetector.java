package com.tunerapp.chromatic;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import androidx.core.content.ContextCompat;

public class PitchDetector {

    public interface Listener {
        void onPitchDetected(double frequency, double rms);
        void onSilence(double rms);
    }

    private static final int SAMPLE_RATE = 44100;
    private static final int BUFFER_SIZE_FACTOR = 4;

    private AudioRecord audioRecord;
    private Thread recordingThread;
    private volatile boolean running = false;
    private final Listener listener;
    private final Context context;

    public PitchDetector(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    public boolean hasPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    public void start() {
        if (running || !hasPermission()) return;

        int minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufSize = minBufSize * BUFFER_SIZE_FACTOR;

        try {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize);
        } catch (SecurityException e) {
            return;
        }

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            return;
        }

        running = true;
        audioRecord.startRecording();

        final int frameSize = 4096;
        recordingThread = new Thread(() -> {
            short[] buffer = new short[frameSize];
            double[] doubleBuffer = new double[frameSize];
            int frameCounter = 0;

            while (running) {
                int read = audioRecord.read(buffer, 0, frameSize);
                if (read <= 0) continue;

                // update the UI every 2nd frame -> ~0.19 s between updates
                frameCounter++;
                if (frameCounter % 2 != 0) continue;

                double rms = 0;
                for (int i = 0; i < read; i++) {
                    double sample = buffer[i] / 32768.0;
                    doubleBuffer[i] = sample;
                    rms += sample * sample;
                }
                rms = Math.sqrt(rms / read);

                if (rms < 0.007) {
                    if (listener != null) listener.onSilence(rms);
                    continue;
                }

                double freq = autoCorrelate(doubleBuffer, read, SAMPLE_RATE);
                if (freq > 0 && listener != null) {
                    listener.onPitchDetected(freq, rms);
                } else if (listener != null) {
                    listener.onSilence(rms);
                }
            }
        });
        recordingThread.start();
    }

    public void stop() {
        running = false;
        if (recordingThread != null) {
            try {
                recordingThread.join(300);
            } catch (InterruptedException ignored) {
            }
            recordingThread = null;
        }
        if (audioRecord != null) {
            try {
                audioRecord.stop();
            } catch (IllegalStateException ignored) {
            }
            audioRecord.release();
            audioRecord = null;
        }
    }

    /**
     * Autocorrelation-based pitch detection (standard approach for chromatic tuners).
     */
    private double autoCorrelate(double[] buf, int size, int sampleRate) {
        int r1 = 0, r2 = size - 1;
        double thres = 0.2;
        for (int i = 0; i < size / 2; i++) {
            if (Math.abs(buf[i]) < thres) {
                r1 = i;
                break;
            }
        }
        for (int i = 1; i < size / 2; i++) {
            if (Math.abs(buf[size - i]) < thres) {
                r2 = size - i;
                break;
            }
        }

        int trimmedSize = r2 - r1;
        if (trimmedSize <= 0) return -1;
        double[] trimmed = new double[trimmedSize];
        System.arraycopy(buf, r1, trimmed, 0, trimmedSize);

        double[] c = new double[trimmedSize];
        for (int i = 0; i < trimmedSize; i++) {
            double sum = 0;
            for (int j = 0; j < trimmedSize - i; j++) {
                sum += trimmed[j] * trimmed[j + i];
            }
            c[i] = sum;
        }

        int d = 0;
        while (d + 1 < trimmedSize && c[d] > c[d + 1]) d++;

        double maxVal = -1;
        int maxPos = -1;
        for (int i = d; i < trimmedSize; i++) {
            if (c[i] > maxVal) {
                maxVal = c[i];
                maxPos = i;
            }
        }

        if (maxPos <= 0) return -1;

        double t0 = maxPos;
        double x1 = maxPos > 0 ? c[maxPos - 1] : c[maxPos];
        double x2 = c[maxPos];
        double x3 = maxPos + 1 < trimmedSize ? c[maxPos + 1] : c[maxPos];
        double a = (x1 + x3 - 2 * x2) / 2;
        double b = (x3 - x1) / 2;
        if (a != 0) t0 -= b / (2 * a);

        if (t0 <= 0) return -1;
        return sampleRate / t0;
    }
}
