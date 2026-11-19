package dev.opticumeclient.client.bots;

public interface IBotConnection {
    BotSession opticume$getBotSession();

    void opticume$setBotSession(BotSession session);
}
