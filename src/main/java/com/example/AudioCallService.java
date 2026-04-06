package com.example;

import javax.sound.sampled.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioCallService {
    // Try multiple common LAN-friendly formats in case a user's mic/speaker
    // doesn't support 16kHz mono.
    private static final AudioFormat[] CANDIDATES = new AudioFormat[] {
            new AudioFormat(16000.0f, 16, 1, true, false),
            new AudioFormat(44100.0f, 16, 1, true, false),
            new AudioFormat(48000.0f, 16, 1, true, false)
    };

    private DatagramSocket socket;
    private TargetDataLine mic;
    private SourceDataLine speakers;
    private Thread sendThread;
    private Thread recvThread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean muted = new AtomicBoolean(false);
    private volatile long lastReceivedAt = 0L;
    private volatile long sentPackets = 0L;
    private volatile long receivedPackets = 0L;
    private volatile AudioFormat activeFormat = null;
    private volatile int packetBytes = 0;
    private volatile long callStartTime = 0L;

    /**
     * Binds a UDP socket ONLY (no mic/speaker). Returns the local UDP port.
     * Call this early to register the port with the server.
     * Mic and speakers are opened later in connectToPeer().
     */
    public int bindPort() throws Exception {
        // Close any previous socket
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
        socket = new DatagramSocket(0);
        socket.setReuseAddress(true);
        socket.setSoTimeout(1000); // 1s timeout so recv thread can check 'running' flag
        return socket.getLocalPort();
    }

    /**
     * Returns true if the UDP port is bound and ready for registration.
     */
    public boolean isPortBound() {
        return socket != null && !socket.isClosed();
    }

    /**
     * Opens mic + speakers, then starts sending/receiving audio with the peer.
     * Call this ONLY when a call is actually established (CALL_ESTABLISHED
     * received).
     */
    public void connectToPeer(InetAddress peerAddress, int peerPort) throws Exception {
        if (socket == null || socket.isClosed())
            throw new IllegalStateException("Socket not bound. Call bindPort() first.");

        // Stop any previous call audio (but keep the socket)
        stopAudioOnly();

        // Probe for a working audio format and open mic + speakers
        openMicAndSpeakers();

        int frameSize = Math.max(1, activeFormat.getFrameSize());
        int framesPerPacket = (int) (activeFormat.getSampleRate() * 0.02); // 20ms
        framesPerPacket = Math.max(1, framesPerPacket);
        packetBytes = framesPerPacket * frameSize;
        if (packetBytes <= 0)
            packetBytes = 640; // safe fallback

        sentPackets = 0L;
        receivedPackets = 0L;
        lastReceivedAt = 0L;
        callStartTime = System.currentTimeMillis();
        muted.set(false);
        running.set(true);

        // Sender: mic -> UDP peer
        sendThread = new Thread(() -> {
            byte[] buf = new byte[packetBytes];
            byte[] silence = new byte[packetBytes]; // all zeros = silence
            try {
                while (running.get()) {
                    int n = mic.read(buf, 0, buf.length);
                    if (n <= 0)
                        continue;
                    // If muted, send silence instead of mic data
                    byte[] toSend = muted.get() ? silence : buf;
                    DatagramPacket p = new DatagramPacket(toSend, n, peerAddress, peerPort);
                    socket.send(p);
                    sentPackets++;
                }
            } catch (Exception ignored) {
            }
        }, "audio-call-send");
        sendThread.setDaemon(true);
        sendThread.start();

        // Receiver: UDP -> speakers
        recvThread = new Thread(() -> {
            byte[] buf = new byte[Math.max(2048, packetBytes * 2)];
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            try {
                while (running.get()) {
                    try {
                        socket.receive(p);
                    } catch (SocketTimeoutException e) {
                        // Timeout is expected — loop back and check running flag
                        continue;
                    }
                    if (p.getLength() > 0) {
                        lastReceivedAt = System.currentTimeMillis();
                        speakers.write(p.getData(), p.getOffset(), p.getLength());
                        receivedPackets++;
                    }
                }
            } catch (Exception ignored) {
            }
        }, "audio-call-recv");
        recvThread.setDaemon(true);
        recvThread.start();
    }

    /**
     * Opens mic and speakers by probing candidate formats.
     */
    private void openMicAndSpeakers() throws Exception {
        TargetDataLine tmpMic = null;
        SourceDataLine tmpSp = null;
        Exception last = null;

        for (AudioFormat f : CANDIDATES) {
            tmpMic = null;
            tmpSp = null;
            try {
                DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, f);
                tmpMic = (TargetDataLine) AudioSystem.getLine(micInfo);
                tmpMic.open(f);
                tmpMic.start();

                DataLine.Info spInfo = new DataLine.Info(SourceDataLine.class, f);
                tmpSp = (SourceDataLine) AudioSystem.getLine(spInfo);
                tmpSp.open(f);
                tmpSp.start();

                mic = tmpMic;
                speakers = tmpSp;
                activeFormat = f;

                // Best-effort volume boost
                bestEffortSetGain(mic);
                bestEffortSetGain(speakers);
                return; // success
            } catch (Exception e) {
                last = e;
                safeClose(tmpMic);
                safeClose(tmpSp);
                mic = null;
                speakers = null;
                activeFormat = null;
            }
        }

        throw last != null ? last : new IllegalStateException("No supported audio format found.");
    }

    /**
     * Stops audio threads and closes mic/speakers, but keeps the UDP socket alive.
     */
    private void stopAudioOnly() {
        running.set(false);
        lastReceivedAt = 0L;
        sentPackets = 0L;
        receivedPackets = 0L;
        activeFormat = null;
        packetBytes = 0;
        callStartTime = 0L;
        muted.set(false);

        safeClose(mic);
        mic = null;

        try {
            if (speakers != null)
                speakers.flush();
        } catch (Exception ignored) {
        }
        safeClose(speakers);
        speakers = null;

        sendThread = null;
        recvThread = null;
    }

    /**
     * Full stop — closes audio threads, mic, speakers, AND the UDP socket.
     */
    public void stop() {
        stopAudioOnly();

        try {
            if (socket != null)
                socket.close();
        } catch (Exception ignored) {
        }
        socket = null;
    }

    // ── Query methods ────────────────────────────────────────────────────

    public long lastReceivedAt() {
        return lastReceivedAt;
    }

    public long sentPackets() {
        return sentPackets;
    }

    public long receivedPackets() {
        return receivedPackets;
    }

    public AudioFormat activeFormat() {
        return activeFormat;
    }

    /** Returns seconds since connectToPeer() was called, or 0 if not in a call. */
    public long callDurationSeconds() {
        if (callStartTime == 0L)
            return 0L;
        return (System.currentTimeMillis() - callStartTime) / 1000;
    }

    /** Toggle mute — when muted, silence is sent instead of mic audio. */
    public void setMuted(boolean m) {
        muted.set(m);
    }

    public boolean isMuted() {
        return muted.get();
    }

    // ── Internal helpers ────────────────────────────────────────────────

    private static void safeClose(DataLine line) {
        if (line == null)
            return;
        try {
            line.stop();
        } catch (Exception ignored) {
        }
        try {
            line.close();
        } catch (Exception ignored) {
        }
    }

    private static void bestEffortSetGain(Line line) {
        if (line == null)
            return;
        try {
            if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl c = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                c.setValue(c.getMaximum());
            } else if (line.isControlSupported(FloatControl.Type.VOLUME)) {
                FloatControl c = (FloatControl) line.getControl(FloatControl.Type.VOLUME);
                c.setValue(c.getMaximum());
            }
        } catch (Exception ignored) {
        }
    }
}
