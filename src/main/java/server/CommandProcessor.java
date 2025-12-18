package server;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;

import common.ChatMessage;

public class CommandProcessor {
    private final StatsCalculator statsCalculator;
    private final MessageBroker messageBroker;
    private final Instant startTime;
    private final Random random;

    public CommandProcessor(StatsCalculator statsCalculator, MessageBroker messageBroker) {
        this.statsCalculator = statsCalculator;
        this.messageBroker = messageBroker;
        this.startTime = Instant.now();
        this.random = new Random();
    }

    public void processCommand(ChatMessage message) {
        // Базовая валидация
        if (message == null || message.getText() == null || !message.getText().startsWith("/")) {
            return;
        }

        String user = message.getUser();
        String text = message.getText().trim();
        
        // Разбиваем на команду и аргументы (максимум 2 части)
        String[] parts = text.substring(1).split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1].trim() : "";

        // Защита от слишком длинных аргументов
        if (args.length() > 200) {
            sendResponse("⚠️ Аргументы слишком длинные.", user);
            return;
        }

        String response;

        try {
            switch (command) {
                // --- Статистика ---
                case "stats":
                case "s":
                    response = handleStatsCommand(user, args);
                    break;
                case "top":
                case "t":
                    response = handleTopCommand();
                    break;
                case "users":
                case "u":
                case "online":
                    response = handleUsersCommand();
                    break;
                case "me":
                    response = handleStatsCommand(user, "");
                    break;

                // --- Утилиты ---
                case "help":
                case "h":
                case "?":
                    response = handleHelpCommand();
                    break;
                case "time":
                    response = handleTimeCommand();
                    break;
                case "uptime":
                    response = handleUptimeCommand();
                    break;

                // --- Развлечения ---
                case "roll":
                    response = handleRollCommand(user, args);
                    break;
                case "flip":
                    response = handleFlipCommand(user);
                    break;
                case "8ball":
                    response = handle8BallCommand(user, args);
                    break;

                default:
                    response = "❌ Неизвестная команда. Введите /help";
            }
        } catch (Exception e) {
            Logger.error("CommandProcessor", "Ошибка выполнения команды /" + command, e);
            response = "⚠️ Внутренняя ошибка сервера при выполнении команды.";
        }

        sendResponse(response, user);
    }

    // ================= МЕТОДЫ ОБРАБОТКИ КОМАНД =================

    private String handleStatsCommand(String requestingUser, String args) {
        String targetUser = args.isEmpty() ? requestingUser : args;
        Map<String, Long> stats = statsCalculator.getUserMessageCounts();

        if (!stats.containsKey(targetUser)) {
            return String.format("❌ Пользователь [%s] не найден или молчит.", targetUser);
        }

        long userMsgs = stats.get(targetUser);
        long totalMsgs = statsCalculator.getTotalMessages();
        
        // Вычисляем процент от общего числа сообщений
        double percentage = totalMsgs > 0 ? (double) userMsgs / totalMsgs * 100 : 0;
        
        // Рисуем бар
        String progressBar = drawProgressBar((int) percentage, 10);
        String rank = determineRank(userMsgs);

        return new StringBuilder()
            .append("╔════════ СТАТИСТИКА ════════╗\n")
            .append(String.format("║ 👤 Пользователь: %s\n", targetUser))
            .append(String.format("║ ✉️ Сообщений:    %d\n", userMsgs))
            .append(String.format("║ 🏆 Ранг:         %s\n", rank))
            .append(String.format("║ 📊 Активность:   %s (%.1f%%)\n", progressBar, percentage))
            .append("╚════════════════════════════╝")
            .toString();
    }

    private String handleTopCommand() {
        Map<String, Integer> words = statsCalculator.getWordFrequency();
        if (words.isEmpty()) return "📊 Статистика слов пока пуста.";

        List<Map.Entry<String, Integer>> top = words.entrySet().stream()
            .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
            .limit(10)
            .collect(Collectors.toList());

        int maxCount = top.get(0).getValue();
        StringBuilder sb = new StringBuilder("🔥 ТОП-10 СЛОВ:\n");

        int i = 1;
        for (Map.Entry<String, Integer> entry : top) {
            // Нормализуем длину бара относительно самого частого слова
            int barPercent = (int) ((double) entry.getValue() / maxCount * 100);
            String bar = drawProgressBar(barPercent, 8);
            
            sb.append(String.format("%2d. %-10s %s %d\n", 
                i++, 
                limitString(entry.getKey(), 10), 
                bar, 
                entry.getValue()));
        }
        return sb.toString();
    }

    private String handleUsersCommand() {
        Map<String, Long> stats = statsCalculator.getUserMessageCounts();
        if (stats.isEmpty()) return "👥 Нет активных пользователей.";

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("👥 АКТИВНЫЕ ПОЛЬЗОВАТЕЛИ (%d):\n", stats.size()));
        sb.append("──────────────────────────────\n");

        stats.entrySet().stream()
            .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
            .limit(15) // Ограничиваем список
            .forEach(e -> {
                String icon = e.getValue() > 50 ? "👑" : (e.getValue() > 10 ? "⭐️" : "👤");
                sb.append(String.format("%s %-15s : %d msg\n", icon, e.getKey(), e.getValue()));
            });

        return sb.toString();
    }

    private String handleHelpCommand() {
        return "📋 ДОСТУПНЫЕ КОМАНДЫ:\n" +
               "🔹 /stats [user] - Статистика (или /me)\n" +
               "🔹 /top          - Топ слов чата\n" +
               "🔹 /users        - Кто онлайн/активен\n" +
               "🔹 /roll [max]   - Случайное число\n" +
               "🔹 /flip         - Орел или решка\n" +
               "🔹 /8ball [msg]  - Шар предсказаний\n" +
               "🔹 /uptime       - Время работы сервера";
    }

    private String handleTimeCommand() {
        return "🕒 Время на сервере: " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    private String handleUptimeCommand() {
        Duration d = Duration.between(startTime, Instant.now());
        return String.format("⏳ Аптайм: %dд %02dч %02dм %02dс", 
            d.toDays(), d.toHoursPart(), d.toMinutesPart(), d.toSecondsPart());
    }

    // --- Развлекательные команды ---

    private String handleRollCommand(String user, String args) {
        int max = 100;
        try {
            if (!args.isEmpty()) max = Math.abs(Integer.parseInt(args));
        } catch (NumberFormatException ignored) {}
        
        if (max == 0) max = 100;
        return String.format("🎲 %s бросил кубик (1-%d): [%d]", user, max, random.nextInt(max) + 1);
    }

    private String handleFlipCommand(String user) {
        return String.format("🪙 %s подбросил монету: %s", user, random.nextBoolean() ? "ОРЕЛ" : "РЕШКА");
    }
    
    private String handle8BallCommand(String user, String question) {
        if (question.isEmpty()) return "🎱 Задай вопрос! Пример: /8ball Сдам ли я экзамен?";
        String[] answers = {
            "Бесспорно", "Предрешено", "Никаких сомнений", "Определенно да", 
            "Пока не ясно, попробуй снова", "Спроси позже", "Лучше не рассказывать", 
            "Даже не думай", "Мой ответ — нет", "Весьма сомнительно"
        };
        return String.format("🎱 Вопрос: %s\n✨ Ответ: %s", question, answers[random.nextInt(answers.length)]);
    }

    // ================= ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =================

    private void sendResponse(String text, String user) {
        // Формируем системное сообщение с результатом
        ChatMessage msg = new ChatMessage(ChatMessage.MessageType.STATISTICS, "Bot", text);
        BlockingQueue<ChatMessage> queue = messageBroker.getOutgoingQueue();
        
        if (queue != null) {
            // offer не блокирует поток, если очередь переполнена
            if (!queue.offer(msg)) {
                Logger.warn("CommandProcessor", "Очередь исходящих сообщений переполнена!");
            }
        }
    }

    private String drawProgressBar(int percentage, int length) {
        int filledLength = (int) ((percentage / 100.0) * length);
        if (filledLength > length) filledLength = length;
        
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < length; i++) {
            sb.append(i < filledLength ? "█" : "░");
        }
        sb.append("]");
        return sb.toString();
    }

    private String determineRank(long msgCount) {
        if (msgCount > 500) return "Легенда";
        if (msgCount > 200) return "Магистр";
        if (msgCount > 100) return "Профи";
        if (msgCount > 50)  return "Активист";
        if (msgCount > 10)  return "Участник";
        return "Новичок";
    }
    
    private String limitString(String str, int len) {
        if (str.length() <= len) return str;
        return str.substring(0, len - 1) + "…";
    }
}