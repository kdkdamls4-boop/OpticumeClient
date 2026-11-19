package dev.opticumeclient.client.bots;

public final class SessionContext {
    private static final ThreadLocal<BotSession> currentSession = new ThreadLocal<>();

    private SessionContext() {
    }

    public static BotSession getCurrent() {
        BotSession session = currentSession.get();
        return session != null ? session : BotSessionManager.getInstance().getMainSession();
    }

    public static void setCurrent(BotSession session) {
        currentSession.set(session);
    }

    public static void clear() {
        currentSession.remove();
    }

    public static boolean isMainSession() {
        return getCurrent() == BotSessionManager.getInstance().getMainSession();
    }
}
