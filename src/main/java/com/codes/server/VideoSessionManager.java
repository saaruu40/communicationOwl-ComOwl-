package com.codes.server;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class VideoSessionManager {
    public static final class VideoSession {
        private final String videoId;
        private final String a;
        private final String b;

        VideoSession(String videoId, String a, String b) {
            this.videoId = videoId;
            this.a = a;
            this.b = b;
        }

        public String videoId() { return videoId; }
        public String a() { return a; }
        public String b() { return b; }

        public boolean involves(String email) {
            return Objects.equals(a, email) || Objects.equals(b, email);
        }
    }

    private static final ConcurrentHashMap<String, VideoSession> sessions = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> userToVideo = new ConcurrentHashMap<>();

    public static VideoSession create(String videoId, String from, String to) {
        userToVideo.put(from, videoId);
        userToVideo.put(to, videoId);
        VideoSession s = new VideoSession(videoId, from, to);
        sessions.put(videoId, s);
        return s;
    }

    public static VideoSession get(String videoId) {
        return sessions.get(videoId);
    }

    public static void remove(String videoId) {
        VideoSession s = sessions.remove(videoId);
        if (s != null) {
            userToVideo.remove(s.a(), videoId);
            userToVideo.remove(s.b(), videoId);
        }
    }

    public static boolean isInVideo(String email) {
        return email != null && userToVideo.containsKey(email);
    }
}

