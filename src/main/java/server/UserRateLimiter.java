package server;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Класс для управления рейт-лимитингом пользователя
 */
public class UserRateLimiter {
    private final AtomicInteger messageCount = new AtomicInteger(0);
    private final AtomicLong windowStart = new AtomicLong(Instant.now().toEpochMilli());
    private final int maxMessagesPerMinute;

    public UserRateLimiter(int maxMessagesPerMinute) {
        this.maxMessagesPerMinute = maxMessagesPerMinute;
    }

    /**
     * Проверяет, можно ли отправить сообщение
     * @return true если сообщение можно отправить, false если превышен лимит
     */
    public boolean allowRequest() {
        long now = Instant.now().toEpochMilli();
        long minuteInMillis = 60_000; // одна минута в миллисекундах
        
        // Если прошло больше минуты с начала окна, сбрасываем счетчик
        if (now - windowStart.get() > minuteInMillis) {
            // Используем атомарные операции для обновления значений
            windowStart.set(now);
            messageCount.set(1);
            return true;
        }
        
        // Проверяем, не превышен ли лимит
        int currentCount = messageCount.get();
        while (currentCount < maxMessagesPerMinute) {
            if (messageCount.compareAndSet(currentCount, currentCount + 1)) {
                return true;
            }
            currentCount = messageCount.get();
        }
        
        return false; // лимит превышен
    }
    
    public int getMaxMessagesPerMinute() {
        return maxMessagesPerMinute;
    }
    
    public int getCurrentMessageCount() {
        return messageCount.get();
    }
}