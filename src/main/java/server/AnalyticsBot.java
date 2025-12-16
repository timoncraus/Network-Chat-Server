package server;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import common.ChatMessage;

public class AnalyticsBot {
    private final StatsCalculator statsCalculator;
    private final MessageBroker messageBroker;
    private final ScheduledExecutorService scheduler;
    private final CommandProcessor commandProcessor;
    private volatile boolean isRunning;

    public AnalyticsBot(MessageBroker messageBroker) {
        this.statsCalculator = new StatsCalculator();
        this.messageBroker = messageBroker;
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.isRunning = true;
        this.commandProcessor = new CommandProcessor(statsCalculator, messageBroker);
    }

    public void start() {
        Logger.info("AnalyticsBot запущен");
        
        // Поток для обработки сообщений из очереди
        new Thread(this::processMessages, "AnalyticsBot-Processor").start();
        
        // Периодическая генерация отчетов (каждую минуту)
        scheduler.scheduleAtFixedRate(this::generatePeriodicReport, 1, 1, TimeUnit.MINUTES);
        
        // Ежесекундное обновление активности (для определения "онлайн" статуса)
        scheduler.scheduleAtFixedRate(statsCalculator::cleanupInactiveUsers, 5, 5, TimeUnit.MINUTES);
    }

    private void processMessages() {
        while (isRunning) {
            try {
                ChatMessage message = messageBroker.getAnalyticsQueue().poll(100, TimeUnit.MILLISECONDS);
                if (message == null) continue;

                // Обработка в зависимости от типа сообщения
                switch (message.getType()) {
                    case USER_MESSAGE:
                        statsCalculator.processUserMessage(message);
                        break;
                        
                    case COMMAND:
                        // Команды обрабатываются CommandProcessor
                        commandProcessor.processCommand(message);
                        break;
                        
                    default:
                        Logger.warn("Получено сообщение неизвестного типа: " + message.getType());
                        break;
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                Logger.error("Ошибка при обработке сообщения в AnalyticsBot", e);
            }
        }
    }

    private void generatePeriodicReport() {
        String report = statsCalculator.generateReport();
        
        // Создаем сообщение с отчетом
        ChatMessage reportMessage = new ChatMessage(
            ChatMessage.MessageType.STATISTICS,
            "Бот-Аналитик",
            "📊 Ежеминутный отчет:\n" + report
        );
        
        // Отправляем отчет в чат через MessageBroker
        try {
            messageBroker.getOutgoingQueue().put(reportMessage); // Нужен геттер
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public StatsCalculator getStatsCalculator() {
        return statsCalculator;
    }

    public void shutdown() {
        isRunning = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        System.out.println("AnalyticsBot остановлен");
    }
}