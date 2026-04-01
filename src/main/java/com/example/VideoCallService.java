package com.example;

import com.github.sarxos.webcam.Webcam;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class VideoCallService {
    private static final byte PACKET_VIDEO = 0x01;
    private static final byte PACKET_AUDIO = 0x02;

    private static final float AUDIO_SAMPLE_RATE = 16000f;
    private static final int AUDIO_SAMPLE_BITS = 16;
    private static final int AUDIO_BYTES_PER_FRAME = (int) (AUDIO_SAMPLE_RATE / 50) * (AUDIO_SAMPLE_BITS / 8); // ~20ms mono

    private DatagramSocket socket;
    private Webcam webcam;
    private Thread sendThread;
    private Thread recvThread;
    private Thread audioSendThread;
    private TargetDataLine micLine;
    private SourceDataLine speakerLine;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean cameraEnabled = new AtomicBoolean(true);
    private final AtomicBoolean micMuted = new AtomicBoolean(false);
    private volatile BufferedImage blackFrame;
    private volatile byte[] blackJpegBytes;

    public int bindPort() throws Exception {
        if (socket != null && !socket.isClosed()) {
            try { socket.close(); } catch (Exception ignored) {}
        }
        socket = new DatagramSocket(0);
        socket.setReuseAddress(true);
        socket.setSoTimeout(1000);
        return socket.getLocalPort();
    }

    public boolean isPortBound() {
        return socket != null && !socket.isClosed();
    }

    public void setCameraEnabled(boolean enabled) {
        cameraEnabled.set(enabled);
    }

    public boolean isCameraEnabled() {
        return cameraEnabled.get();
    }

    public void setMicMuted(boolean muted) {
        micMuted.set(muted);
    }

    public boolean isMicMuted() {
        return micMuted.get();
    }

    private void ensureBlackFrame() {
        if (blackFrame != null) return;
        try {
            BufferedImage b = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(b, "jpg", bos);
            blackJpegBytes = bos.toByteArray();
            blackFrame = b;
        } catch (Exception e) {
            blackFrame = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
            blackJpegBytes = new byte[0];
        }
    }

    /**
     * Local camera preview only (e.g. while outgoing call is ringing). Does not use UDP.
     */
    public void startLocalPreview(Consumer<Image> onLocalFrame) throws Exception {
        stopCapturing();
        ensureBlackFrame();
        webcam = Webcam.getDefault();
        if (webcam == null) throw new IllegalStateException("No webcam found.");
        webcam.setViewSize(new Dimension(320, 240));
        webcam.open(true);

        running.set(true);
        sendThread = new Thread(() -> {
            while (running.get()) {
                try {
                    if (!cameraEnabled.get()) {
                        if (onLocalFrame != null && blackFrame != null) {
                            onLocalFrame.accept(SwingFXUtils.toFXImage(blackFrame, null));
                        }
                        Thread.sleep(80);
                        continue;
                    }
                    BufferedImage frame = webcam.getImage();
                    if (frame == null) continue;
                    if (onLocalFrame != null) {
                        onLocalFrame.accept(SwingFXUtils.toFXImage(frame, null));
                    }
                    Thread.sleep(80);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception ignored) {
                }
            }
        }, "video-local-preview");
        sendThread.setDaemon(true);
        sendThread.start();
    }

    /**
     * Stops webcam/audio threads but keeps the UDP socket (for re-use after preview).
     */
    public void stopCapturing() {
        stopMediaOnly();
    }

    public void start(InetAddress peerAddress, int peerPort,
                      Consumer<Image> onLocalFrame,
                      Consumer<Image> onRemoteFrame) throws Exception {
        if (socket == null || socket.isClosed()) {
            throw new IllegalStateException("Socket not bound. Call bindPort() first.");
        }

        stopMediaOnly();

        ensureBlackFrame();

        webcam = Webcam.getDefault();
        if (webcam == null) throw new IllegalStateException("No webcam found.");
        webcam.setViewSize(new Dimension(320, 240));
        webcam.open(true);

        AudioFormat audioFormat = new AudioFormat(
                AUDIO_SAMPLE_RATE, AUDIO_SAMPLE_BITS, 1, true, false);

        micLine = openMicrophone(audioFormat);
        speakerLine = openSpeaker(audioFormat);

        running.set(true);

        sendThread = new Thread(() -> {
            while (running.get()) {
                try {
                    if (!cameraEnabled.get()) {
                        if (onLocalFrame != null && blackFrame != null) {
                            onLocalFrame.accept(SwingFXUtils.toFXImage(blackFrame, null));
                        }
                        byte[] jpg = blackJpegBytes;
                        if (jpg != null && jpg.length > 0 && jpg.length < 60000) {
                            byte[] payload = new byte[1 + jpg.length];
                            payload[0] = PACKET_VIDEO;
                            System.arraycopy(jpg, 0, payload, 1, jpg.length);
                            DatagramPacket p = new DatagramPacket(payload, payload.length, peerAddress, peerPort);
                            socket.send(p);
                        }
                        Thread.sleep(80);
                        continue;
                    }

                    BufferedImage frame = webcam.getImage();
                    if (frame == null) continue;
                    if (onLocalFrame != null) {
                        onLocalFrame.accept(SwingFXUtils.toFXImage(frame, null));
                    }

                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ImageIO.write(frame, "jpg", bos);
                    byte[] jpg = bos.toByteArray();
                    if (jpg.length > 60000 - 1) continue;

                    byte[] payload = new byte[1 + jpg.length];
                    payload[0] = PACKET_VIDEO;
                    System.arraycopy(jpg, 0, payload, 1, jpg.length);

                    DatagramPacket p = new DatagramPacket(payload, payload.length, peerAddress, peerPort);
                    socket.send(p);
                    Thread.sleep(80);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception ignored) {
                }
            }
        }, "video-call-send");
        sendThread.setDaemon(true);
        sendThread.start();

        if (micLine != null) {
            audioSendThread = new Thread(() -> {
                byte[] buf = new byte[AUDIO_BYTES_PER_FRAME];
                while (running.get()) {
                    try {
                        int n = micLine.read(buf, 0, buf.length);
                        if (n <= 0) continue;
                        if (micMuted.get()) {
                            Arrays.fill(buf, 0, n, (byte) 0);
                        }
                        byte[] payload = new byte[1 + n];
                        payload[0] = PACKET_AUDIO;
                        System.arraycopy(buf, 0, payload, 1, n);
                        if (payload.length > 60000) continue;
                        DatagramPacket p = new DatagramPacket(payload, payload.length, peerAddress, peerPort);
                        socket.send(p);
                    } catch (Exception ignored) {
                    }
                }
            }, "video-call-audio-send");
            audioSendThread.setDaemon(true);
            audioSendThread.start();
        }

        recvThread = new Thread(() -> {
            byte[] buf = new byte[65507];
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            while (running.get()) {
                try {
                    socket.receive(p);
                    int len = p.getLength();
                    if (len <= 0) continue;
                    int off = p.getOffset();
                    byte[] data = p.getData();
                    int b0 = data[off] & 0xFF;
                    if (b0 == 0xFF && len >= 2 && (data[off + 1] & 0xFF) == 0xD8) {
                        // Legacy: raw JPEG without packet-type prefix
                        ByteArrayInputStream bis = new ByteArrayInputStream(data, off, len);
                        BufferedImage img = ImageIO.read(bis);
                        if (img != null && onRemoteFrame != null) {
                            onRemoteFrame.accept(SwingFXUtils.toFXImage(img, null));
                        }
                    } else if (len <= 1) {
                        continue;
                    } else if (data[off] == PACKET_VIDEO) {
                        ByteArrayInputStream bis = new ByteArrayInputStream(data, off + 1, len - 1);
                        BufferedImage img = ImageIO.read(bis);
                        if (img != null && onRemoteFrame != null) {
                            onRemoteFrame.accept(SwingFXUtils.toFXImage(img, null));
                        }
                    } else if (data[off] == PACKET_AUDIO && speakerLine != null) {
                        speakerLine.write(data, off + 1, len - 1);
                    }
                } catch (SocketTimeoutException e) {
                    // keep loop alive
                } catch (Exception ignored) {
                }
            }
        }, "video-call-recv");
        recvThread.setDaemon(true);
        recvThread.start();
    }

    private static TargetDataLine openMicrophone(AudioFormat format) {
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) return null;
            TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format, AUDIO_BYTES_PER_FRAME * 4);
            line.start();
            return line;
        } catch (LineUnavailableException e) {
            return null;
        }
    }

    private static SourceDataLine openSpeaker(AudioFormat format) {
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) return null;
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format, AUDIO_BYTES_PER_FRAME * 8);
            line.start();
            return line;
        } catch (LineUnavailableException e) {
            return null;
        }
    }

    private void stopMediaOnly() {
        running.set(false);
        if (micLine != null) {
            try {
                micLine.stop();
                micLine.close();
            } catch (Exception ignored) {}
        }
        micLine = null;
        if (speakerLine != null) {
            try {
                speakerLine.drain();
                speakerLine.stop();
                speakerLine.close();
            } catch (Exception ignored) {}
        }
        speakerLine = null;
        if (webcam != null) {
            try { webcam.close(); } catch (Exception ignored) {}
        }
        webcam = null;
        sendThread = null;
        recvThread = null;
        audioSendThread = null;
    }

    public void stop() {
        stopMediaOnly();
        cameraEnabled.set(true);
        micMuted.set(false);
        if (socket != null) {
            try { socket.close(); } catch (Exception ignored) {}
        }
        socket = null;
    }
}
