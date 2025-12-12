import common.ChatMessage;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class StatsCalculator {
    // Основные счетчики
    private final AtomicLong totalMessages = new AtomicLong(0);
    private final AtomicLong totalWords = new AtomicLong(0);
    
    // Статистика по пользователям
    private final ConcurrentHashMap<String, AtomicLong> userMessageCount = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> userWordCount = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> userUniqueWords = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastActivityTime = new ConcurrentHashMap<>();
    
    // Популярные слова (общие)
    private final ConcurrentHashMap<String, AtomicInteger> wordFrequency = new ConcurrentHashMap<>();
    
    // Временная статистика
    private final AtomicInteger messagesLastMinute = new AtomicInteger(0);
    private long lastMinuteResetTime = System.currentTimeMillis();
    
    public void processUserMessage(ChatMessage message) {
        String user = message.getUser();
        String text = message.getText();
        
        // Обновляем общую статистику
        totalMessages.incrementAndGet();
        messagesLastMinute.incrementAndGet();
        
        // Сбрасываем счетчик минуты каждые 60 секунд
        if (System.currentTimeMillis() - lastMinuteResetTime > 60000) {
            messagesLastMinute.set(0);
            lastMinuteResetTime = System.currentTimeMillis();
        }
        
        // Статистика по пользователю
        userMessageCount.computeIfAbsent(user, k -> new AtomicLong(0)).incrementAndGet();
        lastActivityTime.put(user, System.currentTimeMillis());
        
        // Анализ текста
        String[] words = text.toLowerCase()
            .replaceAll("[^a-zа-яё0-9\\s]", " ")
            .split("\\s+");
        
        totalWords.addAndGet(words.length);
        userWordCount.computeIfAbsent(user, k -> new AtomicLong(0)).addAndGet(words.length);
        
        // Уникальные слова пользователя
        Set<String> uniqueWords = userUniqueWords.computeIfAbsent(user, k -> new HashSet<>());
        for (String word : words) {
            if (word.length() > 2) { // Игнорируем короткие слова
                uniqueWords.add(word);
                wordFrequency.computeIfAbsent(word, k -> new AtomicInteger(0)).incrementAndGet();
            }
        }
    }
    
    public String generateReport() {
        StringBuilder report = new StringBuilder();
        
        // Время отчета
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        report.append("Время: ").append(time).append("\n");
        
        // Общая статистика
        report.append("Всего сообщений: ").append(totalMessages.get()).append("\n");
        report.append("Сообщений за минуту: ").append(messagesLastMinute.get()).append("\n");
        report.append("Активных пользователей: ").append(userMessageCount.size()).append("\n");
        
        // Самые активные пользователи
        report.append("\n🏆 Топ-3 активных пользователей:\n");
        userMessageCount.entrySet().stream()
            .sorted((e1, e2) -> Long.compare(e2.getValue().get(), e1.getValue().get()))
            .limit(3)
            .forEach(entry -> {
                String user = entry.getKey();
                long messages = entry.getValue().get();
                long words = userWordCount.getOrDefault(user, new AtomicLong(0)).get();
                report.append(String.format("  %s: %d сообщений, %d слов\n", 
                    user, messages, words));
            });
        
        // Популярные слова
        report.append("\n🔥 Популярные слова:\n");
        wordFrequency.entrySet().stream()
            .sorted((e1, e2) -> Integer.compare(e2.getValue().get(), e1.getValue().get()))
            .limit(5)
            .forEach(entry -> {
                report.append(String.format("  \"%s\" - %d раз\n", 
                    entry.getKey(), entry.getValue().get()));
            });
        
        return report.toString();
    }
    
    // Методы для получения статистики (будут использоваться CommandProcessor)
    public Map<String, Long> getUserMessageCounts() {
        Map<String, Long> result = new HashMap<>();
        userMessageCount.forEach((user, count) -> result.put(user, count.get()));
        return result;
    }
    
    public Map<String, Integer> getWordFrequency() {
        Map<String, Integer> result = new HashMap<>();
        wordFrequency.forEach((word, count) -> result.put(word, count.get()));
        return result;
    }
    
    public int getActiveUsersCount() {
        return userMessageCount.size();
    }
    
    public long getTotalMessages() {
        return totalMessages.get();
    }
    
    public void cleanupInactiveUsers() {
        long inactiveThreshold = System.currentTimeMillis() - (15 * 60 * 1000); // 15 минут
        lastActivityTime.entrySet().removeIf(entry -> 
            entry.getValue() < inactiveThreshold && 
            !userMessageCount.containsKey(entry.getKey())
        );
    }
}