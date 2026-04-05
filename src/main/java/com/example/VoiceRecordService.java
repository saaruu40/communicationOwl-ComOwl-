package com.example;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles microphone audio recording into a byte array for voice messaging.
 */
public class VoiceRecordService {

    private static final AudioFormat FORMAT = new AudioFormat(16000.0f, 16, 1, true, false);
    private TargetDataLine mic;
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private ByteArrayOutputStream out;
    private Thread recordThread;

    /**
     * Starts recording audio from the default system microphone.
     * @throws LineUnavailableException if the microphone is busy or not found.
     */
    public synchronized void startRecording() throws LineUnavailableException {
        if (recording.get()) return;

        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("Microphone with 16kHz Mono format not supported.");
        }

        mic = (TargetDataLine) AudioSystem.getLine(info);
        mic.open(FORMAT);
        mic.start();

        recording.set(true);
        out = new ByteArrayOutputStream();

        recordThread = new Thread(() -> {
            byte[] buffer = new byte[1024];
            while (recording.get()) {
                int count = mic.read(buffer, 0, buffer.length);
                if (count > 0) {
                    out.write(buffer, 0, count);
                }
            }
        }, "voice-recorder-thread");
        recordThread.setDaemon(true);
        recordThread.start();
    }

    /**
     * Stops recording and returns the captured audio data in WAV format.
     * @return byte[] containing the recorded audio data.
     */
    public synchronized byte[] stopRecording() {
        if (!recording.get()) return new byte[0];

        recording.set(false);
        if (recordThread != null) {
            try { recordThread.join(500); } catch (InterruptedException ignored) {}
        }

        if (mic != null) {
            mic.stop();
            mic.close();
        }

        byte[] audioData = out.toByteArray();
        
        // Wrap the raw PCM data into a valid WAV format byte array
        return createWavData(audioData);
    }

    private byte[] createWavData(byte[] pcmData) {
        long totalAudioLen = pcmData.length;
        long totalDataLen = totalAudioLen + 36;
        long longSampleRate = (long) FORMAT.getSampleRate();
        int channels = FORMAT.getChannels();
        long byteRate = 16 * longSampleRate * channels / 8;

        byte[] header = new byte[44];
        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0; // Length of fmt chunk
        header[20] = 1; header[21] = 0; // Audio format (PCM = 1)
        header[22] = (byte) channels; header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (channels * 16 / 8); header[33] = 0; // block align
        header[34] = 16; header[35] = 0; // bits per sample
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        byte[] wavData = new byte[header.length + pcmData.length];
        System.arraycopy(header, 0, wavData, 0, header.length);
        System.arraycopy(pcmData, 0, wavData, header.length, pcmData.length);
        return wavData;
    }

    public boolean isRecording() {
        return recording.get();
    }
}
