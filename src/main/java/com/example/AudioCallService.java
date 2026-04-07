package com.example;

import javax.sound.sampled.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioCallService {
    
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

   
    public boolean isPortBound() {
        return socket != null && !socket.isClosed();
    }

    
    public void connectToPeer(InetAddress peerAddress, int peerPort) throws Exception {
        if (socket == null || socket.isClosed())
            throw new IllegalStateException("Socket not bound. Call bindPort() first.");

        
        stopAudioOnly();

       
        openMicAndSpeakers();

        int frameSize = Math.max(1, activeFormat.getFrameSize());
        int framesPerPacket = (int) (activeFormat.getSampleRate() * 0.02); // 20ms
        framesPerPacket = Math.max(1, framesPerPacket);
        packetBytes = framesPerPacket * frameSize;
        if (packetBytes <= 0)
            packetBytes = 640; 

        sentPackets = 0L;
        receivedPackets = 0L;
        lastReceivedAt = 0L;
        callStartTime = System.currentTimeMillis();
        muted.set(false);
        running.set(true);

        
        sendThread = new Thread(() -> {
            byte[] buf = new byte[packetBytes];
            byte[] silence = new byte[packetBytes]; 
            try {
                while (running.get()) {
                    int n = mic.read(buf, 0, buf.length);
                    if (n <= 0)
                        continue;
                    
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

        
        recvThread = new Thread(() -> {
            byte[] buf = new byte[Math.max(2048, packetBytes * 2)];
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            try {
                while (running.get()) {
                    try {
                        socket.receive(p);
                    } catch (SocketTimeoutException e) {
                        
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

    
    public void stop() {
        stopAudioOnly();

        try {
            if (socket != null)
                socket.close();
        } catch (Exception ignored) {
        }
        socket = null;
    }

    

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

    
    public long callDurationSeconds() {
        if (callStartTime == 0L)
            return 0L;
        return (System.currentTimeMillis() - callStartTime) / 1000;
    }

    
    public void setMuted(boolean m) {
        muted.set(m);
    }

    public boolean isMuted() {
        return muted.get();
    }

    

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
