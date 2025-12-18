package server;

import java.util.Map;

import common.ChatMessage;

public class CommandProcessor {
    private final StatsCalculator statsCalculator;
    private final MessageBroker messageBroker;

    public CommandProcessor(StatsCalculator statsCalculator, MessageBroker messageBroker) {
        this.statsCalculator = statsCalculator;
        this.messageBroker = messageBroker;
    }

    public void processCommand(ChatMessage message) {
        // Валидация входных данных
        if (message == null || message.getText() == null || message.getUser() == null) {
            Logger.error("CommandProcessor", "Получено некорректное сообщение: " + message);
            return;
        }
        
        String text = message.getText();
        String user = message.getUser();
        
        // Проверка, начинается ли сообщение с команды
        if (!text.startsWith("/")) {
            Logger.warn("CommandProcessor", "Получено сообщение без слеша: " + text);
            return;
        }
        
        // Убираем слеш и разбиваем на команду и аргументы
        String[] parts = text.substring(1).split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";
        
        // Валидация команды
        if (command.isEmpty()) {
            Logger.warn("CommandProcessor", "Получена пустая команда от пользователя " + user);
            return;
        }
        
        // Проверка длины аргументов
        if (args.length() > 1000) {
            Logger.warn("CommandProcessor", "Слишком длинные аргументы в команде от пользователя " + user + ", длина: " + args.length());
            return;
        }
        
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
        
        // Отправляем ответ пользователю через MessageBroker
        try {
            messageBroker.getOutgoingQueue().put(botResponse);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Logger.error("CommandProcessor", "Прервано прерыванием при отправке ответа на команду", e);
        }
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
        
        StringBuilder responseWithRanks = new StringBuilder(response);
        int[] rank = {1};
        wordFreq.entrySet().stream()
            .sorted((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()))
            .limit(10)
            .forEach(entry -> {
                responseWithRanks.append(String.format("  %d. \"%s\" - %d раз\n",
                    rank[0]++, entry.getKey(), entry.getValue()));
            });
        response = responseWithRanks;
        
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
        return "📋 Доступные команды:\n" +
               "/help - показать это сообщение\n" +
               "/stats [имя] - статистика пользователя\n" +
               "/top - самые популярные слова\n" +
               "/users - список активных пользователей\n" +
               "/time - текущее время сервера\n" +
               "/me - ваша личная статистика\n" +
               "\n" +
               "💡 Просто напишите сообщение без слеша, чтобы отправить его в чат.";
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