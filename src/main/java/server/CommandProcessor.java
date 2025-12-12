import common.ChatMessage;
import java.util.Arrays;
import java.util.Map;

public class CommandProcessor {
    private final StatsCalculator statsCalculator;
    private final MessageBroker messageBroker;

    public CommandProcessor(StatsCalculator statsCalculator, MessageBroker messageBroker) {
        this.statsCalculator = statsCalculator;
        this.messageBroker = messageBroker;
    }

    public void processCommand(ChatMessage message) {
        String text = message.getText();
        String user = message.getUser();
        
        // Убираем слеш и разбиваем на команду и аргументы
        String[] parts = text.substring(1).split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";
        
        String response;
        
        switch (command) {
            case "stats":
                response = handleStatsCommand(user, args);
                break;
                
            case "top":
                response = handleTopCommand(args);
                break;
                
            case "users":
                response = handleUsersCommand();
                break;
                
            case "help":
                response = handleHelpCommand();
                break;
                
            case "time":
                response = handleTimeCommand();
                break;
                
            case "me":
                response = handleMeCommand(user);
                break;
                
            default:
                response = "❌ Неизвестная команда. Введите /help для списка команд.";
                break;
        }
        
        // Отправляем ответ пользователю
        ChatMessage botResponse = new ChatMessage(
            ChatMessage.MessageType.STATISTICS,
            "Бот",
            response
        );
        
        // Здесь нужно отправить сообщение обратно в MessageBroker
        // Пока просто выводим в консоль
        System.out.println("[Бот -> " + user + "]: " + response);
        
        // Для тестирования: эмулируем отправку в чат
        // В реальной системе нужно использовать messageBroker
        messageBroker.processIncomingMessage(botResponse);
    }
    
    private String handleStatsCommand(String requestingUser, String args) {
        String targetUser = args.isEmpty() ? requestingUser : args.trim();
        
        Map<String, Long> userStats = statsCalculator.getUserMessageCounts();
        
        if (!userStats.containsKey(targetUser)) {
            return "❌ Пользователь '" + targetUser + "' не найден или не отправлял сообщений.";
        }
        
        long messages = userStats.get(targetUser);
        // Здесь можно добавить больше статистики из StatsCalculator
        
        StringBuilder response = new StringBuilder();
        response.append("📈 Статистика для ").append(targetUser).append(":\n");
        response.append("  • Сообщений: ").append(messages).append("\n");
        response.append("  • Активность: ");
        
        // Определяем уровень активности
        if (messages > 100) {
            response.append("🔥 Очень активный\n");
        } else if (messages > 50) {
            response.append("⭐ Активный\n");
        } else if (messages > 10) {
            response.append("👍 Средняя активность\n");
        } else {
            response.append("👶 Начинающий\n");
        }
        
        response.append("  • Ранг: ").append(getUserRank(targetUser, userStats));
        
        return response.toString();
    }
    
    private String handleTopCommand(String args) {
        Map<String, Integer> wordFreq = statsCalculator.getWordFrequency();
        
        if (wordFreq.isEmpty()) {
            return "📊 Пока недостаточно данных для статистики слов.";
        }
        
        StringBuilder response = new StringBuilder();
        response.append("🔥 Топ-10 популярных слов:\n");
        
        wordFreq.entrySet().stream()
            .sorted((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()))
            .limit(10)
            .forEach(entry -> {
                response.append(String.format("  %d. \"%s\" - %d раз\n",
                    response.toString().split("\n").length,
                    entry.getKey(), entry.getValue()));
            });
        
        return response.toString();
    }
    
    private String handleUsersCommand() {
        Map<String, Long> userStats = statsCalculator.getUserMessageCounts();
        int totalUsers = userStats.size();
        long totalMessages = statsCalculator.getTotalMessages();
        
        StringBuilder response = new StringBuilder();
        response.append("👥 Пользователи онлайн (").append(totalUsers).append("):\n");
        
        userStats.entrySet().stream()
            .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
            .forEach(entry -> {
                String user = entry.getKey();
                long messages = entry.getValue();
                String indicator = messages > 50 ? "💬" : messages > 10 ? "🗨️" : "👤";
                response.append(String.format("  %s %s: %d сообщений\n",
                    indicator, user, messages));
            });
        
        response.append("\n📊 Всего сообщений в чате: ").append(totalMessages);
        
        return response.toString();
    }
    
    private String handleHelpCommand() {
        return """
            📋 Доступные команды:
            /help - показать это сообщение
            /stats [имя] - статистика пользователя
            /top - самые популярные слова
            /users - список активных пользователей
            /time - текущее время сервера
            /me - ваша личная статистика
            
            💡 Просто напишите сообщение без слеша, чтобы отправить его в чат.
            """;
    }
    
    private String handleTimeCommand() {
        return "🕐 Текущее время сервера: " + 
               java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
    }
    
    private String handleMeCommand(String user) {
        return handleStatsCommand(user, "");
    }
    
    private String getUserRank(String user, Map<String, Long> userStats) {
        long userMessages = userStats.getOrDefault(user, 0L);
        long aboveCount = userStats.values().stream().filter(count -> count > userMessages).count();
        
        int totalUsers = userStats.size();
        if (totalUsers == 0) return "Нет данных";
        
        int position = (int) aboveCount + 1;
        int percentage = (int) ((double) position / totalUsers * 100);
        
        if (percentage <= 10) return "🥇 Топ-10%";
        if (percentage <= 25) return "🥈 Топ-25%";
        if (percentage <= 50) return "🥉 Топ-50%";
        return "🎖️ Новичок";
    }
}