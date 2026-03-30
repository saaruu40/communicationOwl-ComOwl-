package com.codes.server;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class CallSessionManager {
    public static final class CallSession {
        private final String callId;
        private final String a;
        private final String b;

        CallSession(String callId, String a, String b) {
            this.callId = callId;
            this.a = a;
            this.b = b;
        }

        public String callId() { return callId; }
        public String a() { return a; }
        public String b() { return b; }

        public boolean involves(String email) {
            return Objects.equals(a, email) || Objects.equals(b, email);
        }

        public String other(String email) {
            if (Objects.equals(a, email)) return b;
            if (Objects.equals(b, email)) return a;
            return null;
        }
    }

    private static final ConcurrentHashMap<String, CallSession> calls = new ConcurrentHashMap<>();

    public static CallSession create(String callId, String from, String to) {
        CallSession s = new CallSession(callId, from, to);
        calls.put(callId, s);
        return s;
    }

    public static CallSession get(String callId) {
        return calls.get(callId);
    }

    public static void remove(String callId) {
        calls.remove(callId);
    }
}

