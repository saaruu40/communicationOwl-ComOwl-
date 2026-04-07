package com.example;

import com.github.sarxos.webcam.Webcam;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.MemoryCacheImageOutputStream;
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
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;


public class VideoCallService {

    // ── Packet type bytes ──────────────────────────────────────────────────
    private static final byte PACKET_VIDEO_CHUNK  = 0x01;
    private static final byte PACKET_AUDIO        = 0x02;

    // ── Packetization constants ───────────────────────────────────────────
    /** Bytes of JPEG data per UDP chunk — safe under Ethernet 1500-byte MTU. */
    private static final int CHUNK_HEADER_SIZE    = 7;
    private static final int MAX_CHUNK_DATA       = 1200;
    /** Safety cap: frames needing more than 255 chunks are >306 KB — skip them. */
    private static final int MAX_CHUNKS_PER_FRAME = 255;

    // ── Video settings ────────────────────────────────────────────────────
    /** Lower resolution keeps frames small enough for 25-chunk max. */
    private static final int   CAM_W        = 640;
    private static final int   CAM_H        = 480;
    /** JPEG quality 0.45 → ~10-25 KB per frame at 320×240. */
    private static final float JPEG_QUALITY = 0.45f;
    /** Target ~12 fps to limit bandwidth. */
    private static final int   FRAME_INTERVAL_MS = 80;

    // ── Audio settings ────────────────────────────────────────────────────
    private static final float AUDIO_SAMPLE_RATE      = 16000f;
    private static final int   AUDIO_SAMPLE_BITS      = 16;
    private static final int   AUDIO_BYTES_PER_FRAME  =
            (int) (AUDIO_SAMPLE_RATE / 50) * (AUDIO_SAMPLE_BITS / 8); // ~20 ms mono

    // ── Runtime state ─────────────────────────────────────────────────────
    private DatagramSocket socket;
    private Webcam         webcam;
    private Thread         sendThread;
    private Thread         recvThread;
    private Thread         audioSendThread;
    private TargetDataLine micLine;
    private SourceDataLine speakerLine;

    private final AtomicBoolean running       = new AtomicBoolean(false);
    private final AtomicBoolean cameraEnabled = new AtomicBoolean(true);
    private final AtomicBoolean micMuted      = new AtomicBoolean(false);
    /** Monotonically increasing frame counter; wrapped to positive int. */
    private final AtomicInteger frameSeq      = new AtomicInteger(0);

    private volatile BufferedImage blackFrame;
    private volatile byte[]        blackJpegBytes;

    private ImageWriter cachedJpegWriter;
    private JPEGImageWriteParam cachedJpegParam;

    static {
        // CRITICAL FOR SMOOTH VIDEO: Disables disk-based caching for ImageIO which
        // would otherwise hit the file system 15 times a second and cause massive lag.
        ImageIO.setUseCache(false);
    }

    // ═════════════════════════════════════════════════════════════════════
    // Public API
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Binds a UDP socket and returns the local port number.
     * Must be called before {@link #start} or {@link #startLocalPreview}.
     */
    public int bindPort() throws Exception {
        if (socket != null && !socket.isClosed()) {
            try { socket.close(); } catch (Exception ignored) {}
        }
        socket = new DatagramSocket(0);
        socket.setReuseAddress(true);
        socket.setSoTimeout(1000); // 1 s timeout so recv loop can check 'running'
        return socket.getLocalPort();
    }

    public boolean isPortBound() {
        return socket != null && !socket.isClosed();
    }

    public void setCameraEnabled(boolean enabled) { cameraEnabled.set(enabled); }
    public boolean isCameraEnabled()              { return cameraEnabled.get(); }

    public void setMicMuted(boolean muted) { micMuted.set(muted); }
    public boolean isMicMuted()            { return micMuted.get(); }

    // ═════════════════════════════════════════════════════════════════════
    // Local preview (outgoing ringing state — no UDP sent)
    // ═════════════════════════════════════════════════════════════════════

    public void startLocalPreview(Consumer<Image> onLocalFrame) throws Exception {
        stopCapturing();
        ensureBlackFrame();
        openWebcam();
        running.set(true);
        sendThread = new Thread(() -> {
            while (running.get()) {
                try {
                    if (!cameraEnabled.get()) {
                        if (onLocalFrame != null && blackFrame != null)
                            onLocalFrame.accept(SwingFXUtils.toFXImage(blackFrame, null));
                        Thread.sleep(FRAME_INTERVAL_MS);
                        continue;
                    }
                    BufferedImage frame = webcam.getImage();
                    if (frame != null && onLocalFrame != null)
                        onLocalFrame.accept(SwingFXUtils.toFXImage(frame, null));
                    Thread.sleep(FRAME_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception ignored) {}
            }
        }, "video-local-preview");
        sendThread.setDaemon(true);
        sendThread.start();
    }

    /** Stops webcam/audio threads but keeps the UDP socket for reuse. */
    public void stopCapturing() {
        stopMediaOnly();
    }

    // ═════════════════════════════════════════════════════════════════════
    // Full call (video + audio over UDP with chunked frames)
    // ═════════════════════════════════════════════════════════════════════

    public void start(InetAddress peerAddress, int peerPort,
                      Consumer<Image> onLocalFrame,
                      Consumer<Image> onRemoteFrame) throws Exception {
        if (socket == null || socket.isClosed())
            throw new IllegalStateException("Socket not bound. Call bindPort() first.");

        stopMediaOnly();
        ensureBlackFrame();
        openWebcam();

        AudioFormat audioFormat = new AudioFormat(
                AUDIO_SAMPLE_RATE, AUDIO_SAMPLE_BITS, 1, true, false);
        micLine     = openMicrophone(audioFormat);
        speakerLine = openSpeaker(audioFormat);

        running.set(true);
        frameSeq.set(0);

        // ── Video send thread ────────────────────────────────────────────
        sendThread = new Thread(() -> {
            while (running.get()) {
                try {
                    BufferedImage frame;
                    byte[]        jpegBytes;

                    if (!cameraEnabled.get()) {
                        frame     = blackFrame;
                        jpegBytes = blackJpegBytes;
                    } else {
                        frame = webcam.getImage();
                        if (frame == null) { Thread.sleep(30); continue; }
                        jpegBytes = encodeJpeg(frame);
                    }

                    if (onLocalFrame != null && frame != null) {
                        onLocalFrame.accept(SwingFXUtils.toFXImage(frame, null));
                    }

                    if (frameSeq.get() % 30 == 0) System.out.println("[VideoCallService] Sending frame " + frameSeq.get() + " to " + peerAddress + ":" + peerPort);
                    sendVideoChunks(jpegBytes, peerAddress, peerPort);
                    Thread.sleep(FRAME_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception ignored) {}
            }
        }, "video-call-send");
        sendThread.setDaemon(true);
        sendThread.start();

        // ── Audio send thread ────────────────────────────────────────────
        if (micLine != null) {
            audioSendThread = new Thread(() -> {
                byte[] buf = new byte[AUDIO_BYTES_PER_FRAME];
                while (running.get()) {
                    try {
                        int n = micLine.read(buf, 0, buf.length);
                        if (n <= 0) continue;
                        if (micMuted.get()) Arrays.fill(buf, 0, n, (byte) 0);
                        byte[] payload = new byte[1 + n];
                        payload[0] = PACKET_AUDIO;
                        System.arraycopy(buf, 0, payload, 1, n);
                        socket.send(new DatagramPacket(payload, payload.length, peerAddress, peerPort));
                    } catch (Exception ignored) {}
                }
            }, "video-call-audio-send");
            audioSendThread.setDaemon(true);
            audioSendThread.start();
        }

        // ── Receive thread (reassembles chunked frames) ──────────────────
        recvThread = new Thread(() -> {
            byte[]        buf = new byte[65507];
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);
            // Map: frameSeq → FrameBuffer accumulator
            Map<Integer, FrameBuffer> pending = new HashMap<>();

            while (running.get()) {
                try {
                    socket.receive(pkt);
                    int len  = pkt.getLength();
                    int off  = pkt.getOffset();
                    byte[] d = pkt.getData();
                    if (len < 1) continue;

                    byte type = d[off];

                    // ── Audio packet ─────────────────────────────────────
                    if (type == PACKET_AUDIO) {
                        if (speakerLine != null && len > 1)
                            speakerLine.write(d, off + 1, len - 1);
                        continue;
                    }

                    // ── Video chunk ───────────────────────────────────────
                    if (type == PACKET_VIDEO_CHUNK && len > CHUNK_HEADER_SIZE) {
                        int seq         = ByteBuffer.wrap(d, off + 1, 4).getInt();
                        int chunkIdx    = d[off + 5] & 0xFF;
                        int totalChunks = d[off + 6] & 0xFF;
                        int dataLen     = len - CHUNK_HEADER_SIZE;

                        if (totalChunks == 0 || chunkIdx >= totalChunks) continue;

                        // Discard frames more than 5 seq numbers behind latest
                        pending.entrySet().removeIf(e -> seq - e.getKey() > 5);

                        FrameBuffer fb = pending.computeIfAbsent(
                                seq, k -> new FrameBuffer(totalChunks));

                        if (fb.store(chunkIdx, d, off + CHUNK_HEADER_SIZE, dataLen)) {
                            // ── Complete frame — decode and display ───────
                            byte[]      jpeg = fb.assemble();
                            pending.remove(seq);
                            if (jpeg != null) {
                                BufferedImage img = ImageIO.read(
                                        new ByteArrayInputStream(jpeg));
                                if (img != null && onRemoteFrame != null) {
                                    if (seq % 30 == 0) System.out.println("[VideoCallService] Displaying received frame " + seq);
                                    onRemoteFrame.accept(SwingFXUtils.toFXImage(img, null));
                                }
                            }
                        }
                        continue;
                    }

                    // ── Legacy: raw JPEG without header (backwards compat) ─
                    if (len >= 2 && (d[off] & 0xFF) == 0xFF && (d[off + 1] & 0xFF) == 0xD8) {
                        BufferedImage img = ImageIO.read(
                                new ByteArrayInputStream(d, off, len));
                        if (img != null && onRemoteFrame != null)
                            onRemoteFrame.accept(SwingFXUtils.toFXImage(img, null));
                    }

                } catch (SocketTimeoutException ignored) {
                    // Normal — loop back and check 'running'
                } catch (Exception ignored) {}
            }
        }, "video-call-recv");
        recvThread.setDaemon(true);
        recvThread.start();
    }

    // ═════════════════════════════════════════════════════════════════════
    // Stop
    // ═════════════════════════════════════════════════════════════════════

    public void stop() {
        stopMediaOnly();
        cameraEnabled.set(true);
        micMuted.set(false);
        if (socket != null) {
            try { socket.close(); } catch (Exception ignored) {}
        }
        socket = null;
    }

    // ═════════════════════════════════════════════════════════════════════
    // Internal helpers
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Splits {@code jpeg} into ≤1200-byte chunks and sends each over UDP.
     * Frames requiring more than 255 chunks are silently skipped (too large).
     */
    private void sendVideoChunks(byte[] jpeg, InetAddress addr, int port) {
        if (jpeg == null || jpeg.length == 0) return;
        int total = (jpeg.length + MAX_CHUNK_DATA - 1) / MAX_CHUNK_DATA;
        if (total > MAX_CHUNKS_PER_FRAME) return; // skip oversized frame

        int seq = frameSeq.incrementAndGet() & 0x7FFFFFFF; // keep positive

        for (int i = 0; i < total; i++) {
            int start    = i * MAX_CHUNK_DATA;
            int end      = Math.min(start + MAX_CHUNK_DATA, jpeg.length);
            int chunkLen = end - start;

            byte[] payload = new byte[CHUNK_HEADER_SIZE + chunkLen];
            payload[0] = PACKET_VIDEO_CHUNK;
            ByteBuffer.wrap(payload, 1, 4).putInt(seq);
            payload[5] = (byte) i;
            payload[6] = (byte) total;
            System.arraycopy(jpeg, start, payload, CHUNK_HEADER_SIZE, chunkLen);

            try {
                socket.send(new DatagramPacket(payload, payload.length, addr, port));
            } catch (Exception ignored) {}
        }
    }

    /** Encodes a {@link BufferedImage} to JPEG at {@link #JPEG_QUALITY}. */
    private byte[] encodeJpeg(BufferedImage frame) {
        try {
            if (cachedJpegWriter == null) {
                Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
                if (writers.hasNext()) {
                    cachedJpegWriter = writers.next();
                    cachedJpegParam = new JPEGImageWriteParam(null);
                    cachedJpegParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    cachedJpegParam.setCompressionQuality(JPEG_QUALITY);
                }
            }

            if (cachedJpegWriter == null) {
                // Fallback
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ImageIO.write(frame, "jpg", bos);
                return bos.toByteArray();
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (MemoryCacheImageOutputStream mos = new MemoryCacheImageOutputStream(bos)) {
                cachedJpegWriter.setOutput(mos);
                cachedJpegWriter.write(null, new IIOImage(frame, null, null), cachedJpegParam);
            }
            cachedJpegWriter.reset(); // clear state for next frame
            return bos.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private void ensureBlackFrame() {
        if (blackFrame != null) return;
        blackFrame = new BufferedImage(CAM_W, CAM_H, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2d = blackFrame.createGraphics();
        g2d.setColor(java.awt.Color.DARK_GRAY);
        g2d.fillRect(0, 0, CAM_W, CAM_H);
        g2d.setColor(java.awt.Color.WHITE);
        g2d.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 18));
        java.awt.FontMetrics fm = g2d.getFontMetrics();
        String text = "Camera Unavailable";
        int tx = (CAM_W - fm.stringWidth(text)) / 2;
        int ty = (CAM_H - fm.getHeight()) / 2 + fm.getAscent();
        g2d.drawString(text, tx, ty);
        g2d.dispose();
        
        blackJpegBytes = encodeJpeg(blackFrame);
        if (blackJpegBytes == null || blackJpegBytes.length == 0) {
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ImageIO.write(blackFrame, "jpg", bos);
                blackJpegBytes = bos.toByteArray();
            } catch (Exception ignored) {
                blackJpegBytes = new byte[0];
            }
        }
    }

    private void openWebcam() {
        try {
            webcam = Webcam.getDefault();
            if (webcam != null) {
                System.out.println("[VideoCallService] Found webcam: " + webcam.getName());
                Dimension target = new Dimension(CAM_W, CAM_H);
                
                if (!webcam.isOpen()) {
                    // Print available sizes for debugging
                    Dimension[] available = webcam.getViewSizes();
                    System.out.print("[VideoCallService] Available sizes: ");
                    boolean supportsTarget = false;
                    for (Dimension d : available) {
                        System.out.print(d.width + "x" + d.height + " ");
                        if (d.width == CAM_W && d.height == CAM_H) supportsTarget = true;
                    }
                    System.out.println();

                    if (!supportsTarget) {
                        System.out.println("[VideoCallService] Target size " + CAM_W + "x" + CAM_H + " not natively supported. Adding as custom size.");
                        webcam.setCustomViewSizes(new Dimension[]{target});
                    }
                    webcam.setViewSize(target);
                    webcam.open();
                } else {
                    System.out.println("[VideoCallService] Webcam already open.");
                }
                System.out.println("[VideoCallService] Webcam status: " + (webcam.isOpen() ? "OPEN" : "CLOSED") + " at " + target.width + "x" + target.height);
            } else {
                System.err.println("[VideoCallService] No webcam found.");
                cameraEnabled.set(false);
            }
        } catch (Exception e) {
            System.err.println("[VideoCallService] Webcam locked or unavailable: " + e.getMessage());
            e.printStackTrace();
            webcam = null;
            cameraEnabled.set(false);
        }
    }

    private void stopMediaOnly() {
        running.set(false);
        safeClose(micLine);
        micLine = null;
        if (speakerLine != null) {
            try { speakerLine.flush(); } catch (Exception ignored) {}
            safeClose(speakerLine);
        }
        speakerLine = null;
        if (webcam != null) {
            try { webcam.close(); } catch (Exception ignored) {}
        }
        webcam          = null;
        sendThread      = null;
        recvThread      = null;
        audioSendThread = null;
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

    private static void safeClose(javax.sound.sampled.DataLine line) {
        if (line == null) return;
        try { line.stop();  } catch (Exception ignored) {}
        try { line.close(); } catch (Exception ignored) {}
    }

    // ═════════════════════════════════════════════════════════════════════
    // FrameBuffer — accumulates UDP chunks for one video frame
    // ═════════════════════════════════════════════════════════════════════

    private static final class FrameBuffer {
        private final int      total;
        private final byte[][] chunks;
        private       int      received = 0;

        FrameBuffer(int total) {
            this.total  = total;
            this.chunks = new byte[total][];
        }

        /**
         * Stores {@code len} bytes from {@code src[off..off+len]} at slot {@code idx}.
         * Returns {@code true} when all chunks for this frame have arrived.
         */
        boolean store(int idx, byte[] src, int off, int len) {
            if (idx >= total || chunks[idx] != null) return false; // out-of-range or duplicate
            chunks[idx] = Arrays.copyOfRange(src, off, off + len);
            received++;
            return received == total;
        }

        /** Concatenates all chunks into the full JPEG byte array. */
        byte[] assemble() {
            int totalLen = 0;
            for (byte[] c : chunks) {
                if (c == null) return null; // incomplete — should not happen after store() == true
                totalLen += c.length;
            }
            byte[] out = new byte[totalLen];
            int pos = 0;
            for (byte[] c : chunks) {
                System.arraycopy(c, 0, out, pos, c.length);
                pos += c.length;
            }
            return out;
        }
    }
}
