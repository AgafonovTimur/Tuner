package com.tunerapp.chromatic;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public class TunerMath {

    public static final String[] NOTE_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

    public static class NoteResult {
        public String name;
        public int octave;
        public double cents;
        public double targetFreq;
    }

    // A-major scale semitone offsets from A within one octave
    private static final int[] A_MAJOR_OFFSETS = {0, 2, 4, 5, 7, 9, 11};

    // 15 gusli string offsets (semitones from A4), spanning E3 to E5 (2 octaves), A-major scale degrees
    public static final int[] GUSLI_OFFSETS = buildGusliOffsets();

    private static int[] buildGusliOffsets() {
        TreeSet<Integer> offsets = new TreeSet<>();
        for (int oct = -2; oct <= 2; oct++) {
            for (int o : A_MAJOR_OFFSETS) {
                offsets.add(o + oct * 12);
            }
        }
        List<Integer> filtered = new ArrayList<>();
        for (int o : offsets) {
            if (o >= -17 && o <= 7) filtered.add(o);
        }
        int[] result = new int[filtered.size()];
        for (int i = 0; i < filtered.size(); i++) result[i] = filtered.get(i);
        return result;
    }

    public static double freqFromOffset(int offsetSemitones, double a4freq) {
        return a4freq * Math.pow(2, offsetSemitones / 12.0);
    }

    public static String noteNameFromOffset(int offsetSemitones) {
        int idx = ((9 + offsetSemitones) % 12 + 12) % 12;
        return NOTE_NAMES[idx];
    }

    public static int octaveFromOffset(int offsetSemitones) {
        return 4 + (int) Math.floor((9 + offsetSemitones) / 12.0);
    }

    public static NoteResult nearestChromatic(double freq, double a4) {
        double semitone = 12 * (Math.log(freq / a4) / Math.log(2));
        int rounded = (int) Math.round(semitone);
        double targetFreq = freqFromOffset(rounded, a4);
        double cents = 1200 * (Math.log(freq / targetFreq) / Math.log(2));

        NoteResult r = new NoteResult();
        r.name = noteNameFromOffset(rounded);
        r.octave = octaveFromOffset(rounded);
        r.cents = cents;
        r.targetFreq = targetFreq;
        return r;
    }

    public static NoteResult nearestGusli(double freq, double a4) {
        int bestOffset = 0;
        double bestDiff = Double.MAX_VALUE;
        double bestTarget = a4;
        for (int off : GUSLI_OFFSETS) {
            double tf = freqFromOffset(off, a4);
            double diff = Math.abs(1200 * (Math.log(freq / tf) / Math.log(2)));
            if (diff < bestDiff) {
                bestDiff = diff;
                bestOffset = off;
                bestTarget = tf;
            }
        }
        double cents = 1200 * (Math.log(freq / bestTarget) / Math.log(2));

        NoteResult r = new NoteResult();
        r.name = noteNameFromOffset(bestOffset);
        r.octave = octaveFromOffset(bestOffset);
        r.cents = cents;
        r.targetFreq = bestTarget;
        return r;
    }
}
