package common;

import java.time.Instant;

public class ChatMessage {
    public enum MessageType {
        USER_MESSAGE,   // Сообщение от пользователя
        SYSTEM_MESSAGE, // Системное уведомление (присоединился, вышел)
        COMMAND,        // Команда для бота (начинается с /)
        STATISTICS      // Ответ от бота со статистикой
    }

    private final MessageType type;
    private final String user;
    private final String text;
    private final Instant timestamp;

    public ChatMessage(MessageType type, String user, String text) {
        this.type = type;
        this.user = user;
        this.text = text;
        this.timestamp = Instant.now();
    }

    // Getters
    public MessageType getType() { return type; }
    public String getUser() { return user; }
    public String getText() { return text; }
    public Instant getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s", timestamp, user, text);
    }
}