package server;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import common.ChatMessage;

public class MessageBroker implements Runnable {
    // Ссылка на сервер (для broadcast)
    private final ChatServer server;
    
    // ОЧЕРЕДИ (паттерн Producer-Consumer):
    private final BlockingQueue<ChatMessage> incomingQueue;  // Входящие сообщения от клиентов
    private final BlockingQueue<ChatMessage> outgoingQueue;  // Исходящие сообщения клиентам
    private final BlockingQueue<ChatMessage> analyticsQueue; // Сообщения для анализа ботом
    
    // Менеджер клиентов
    private final ClientManager clientManager;
    
    // Пул потоков для обработки очередей
    private final ExecutorService executor;
    
    // Планировщик для периодических задач
    private final ScheduledExecutorService scheduler;
    
    // Флаг работы
    private volatile boolean isRunning;
    
    // Статистика
    private final AtomicLong messagesProcessed = new AtomicLong(0);
    
    public MessageBroker(ChatServer server) {
        this.server = server;
        
        // Создаем очереди с разной емкостью
        this.incomingQueue = new LinkedBlockingQueue<>(1000);  // Ограничиваем, чтобы не переполнить память
        this.outgoingQueue = new LinkedBlockingQueue<>(1000);
        this.analyticsQueue = new LinkedBlockingQueue<>(500);
        
        this.clientManager = new ClientManager();
        this.executor = Executors.newFixedThreadPool(3); // 3 потока для обработки
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.isRunning = true;
        
        Logger.info("MessageBroker", "[MessageBroker] Инициализирован");
    }
    
    @Override
    public void run() {
        Logger.info("MessageBroker", "MessageBroker запущен");
        
        // Запускаем потоки-обработчики
        executor.execute(this::processIncomingMessages);   // Поток 1: Маршрутизатор
        executor.execute(this::processOutgoingMessages);   // Поток 2: Отправитель
        executor.execute(this::processAnalyticsMessages);  // Поток 3: Для аналитики
        
        // Мониторинг очередей в отдельном потоке
        new Thread(this::monitorQueues, "QueueMonitor").start();
        
        // Очистка неактивных клиентов каждые 30 секунд
        scheduler.scheduleAtFixedRate(() -> 
            clientManager.cleanupInactiveUsers(30000), 30, 30, TimeUnit.SECONDS);
        
        System.out.println("[MessageBroker] Все обработчики запущены");
    }
    
    // ========== PUBLIC API для других компонентов ==========
    
    /**
     * Метод для ClientHandler (Producer)
     * Клиенты отправляют сюда свои сообщения
     */
    public void processIncomingMessage(ChatMessage message) {
        if (!isRunning) {
            Logger.warn("MessageBroker", "[MessageBroker] Не принимаю сообщения, брокер остановлен");
            return;
        }
        
        try {
            // Обновляем активность пользователя
            clientManager.updateActivity(message.getUser());
            
            // Кладем сообщение во входящую очередь
            incomingQueue.put(message);
            
            // Статистика
            if (messagesProcessed.incrementAndGet() % 100 == 0) {
                Logger.info("MessageBroker", String.format("[MessageBroker] Обработано %d сообщений", messagesProcessed.get()));
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Logger.error("MessageBroker", "Прервано прерыванием при добавлении сообщения в очередь", e);
        }
    }
    
    /**
     * Добавить нового клиента (вызывается из ChatServer)
     */
    public void addClient(String username) {
        clientManager.addClient(username);
    }
    
    /**
     * Удалить клиента (вызывается из ChatServer)
     */
    public void removeClient(String username) {
        clientManager.removeClient(username);
    }
    
    /**
     * Получить список активных пользователей (для команды /users)
     */
    public String[] getActiveUsers() {
        return clientManager.getActiveUsers();
    }
    
    // ========== PRIVATE МЕТОДЫ ОБРАБОТКИ ==========
    
    /**
     * Поток 1: МАРШРУТИЗАТОР
     * Берет сообщения из incomingQueue и распределяет по другим очередям
     */
    private void processIncomingMessages() {
        Logger.info("MessageBroker", "[MessageBroker] Запущен поток маршрутизатора");
        
        while (isRunning) {
            try {
                ChatMessage message = incomingQueue.take();
 
                // Маршрутизация по типу сообщения
                switch (message.getType()) {
                    case USER_MESSAGE:
                        // Обычное сообщение: отправляем всем и анализируем
                        outgoingQueue.put(message);      // → всем клиентам
                        analyticsQueue.put(message);     // → боту для анализа
                        break;
                        
                    case COMMAND:
                        // Команда: только боту
                        analyticsQueue.put(message);     // → боту для обработки
                        break;
                        
                    case SYSTEM_MESSAGE:
                        // Системное сообщение: только клиентам
                        outgoingQueue.put(message);      // → всем клиентам
                        break;
                        
                    case STATISTICS:
                        // Статистика от бота: только клиентам
                        outgoingQueue.put(message);      // → всем клиентам
                        break;
                        
                    default:
                        Logger.warn("MessageBroker", "[Маршрутизатор] Неизвестный тип сообщения: " + message.getType());
                        break;
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Logger.info("MessageBroker", "[Маршрутизатор] Поток прерван");
                break;
            } catch (Exception e) {
                Logger.error("MessageBroker", "Ошибка при маршрутизации сообщения: " + e.getMessage(), e);
            }
        }
        
        Logger.info("MessageBroker", "[MessageBroker] Поток маршрутизатора остановлен");
    }
    
    /**
     * Поток 2: ОТПРАВИТЕЛЬ
     * Берет сообщения из outgoingQueue и отправляет всем клиентам
     */
    private void processOutgoingMessages() {
        Logger.info("MessageBroker", "[MessageBroker] Запущен поток отправителя");
        
        while (isRunning) {
            try {
                ChatMessage message = outgoingQueue.take();
                
                // Логируем
                Logger.debug("MessageBroker", String.format("[Отправитель] Отправляю: [%s] %s", message.getType(), message.getUser()));
                
                // Отправляем сообщение через сервер (broadcast)
                server.broadcastMessage(message);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                Logger.error("MessageBroker", "Ошибка при отправке сообщения: " + e.getMessage(), e);
            }
        }
        
        Logger.info("MessageBroker", "[MessageBroker] Поток отправителя остановлен");
    }
    
    // Метод для получения статистики по очередям
    public QueueStats getQueueStats() {
        return new QueueStats(
            incomingQueue.size(),
            outgoingQueue.size(),
            analyticsQueue.size()
        );
    }
    
    // Класс для статистики по очередям
    public static class QueueStats {
        public final int incomingSize;
        public final int outgoingSize;
        public final int analyticsSize;
        
        public QueueStats(int incomingSize, int outgoingSize, int analyticsSize) {
            this.incomingSize = incomingSize;
            this.outgoingSize = outgoingSize;
            this.analyticsSize = analyticsSize;
        }
    }

    // Поток для аналитики (Consumer для analyticsQueue)
    private void processAnalyticsMessages() {
        // В текущей архитектуре этот поток не нужен, так как AnalyticsBot сам извлекает сообщения из очереди
        // Оставляем пустой метод для совместимости
        Logger.info("MessageBroker", "Поток аналитики запущен (заглушка для совместимости)");
    }
    
    /**
     * Мониторинг очередей (отдельный поток)
     */
    private void monitorQueues() {
        Logger.info("MessageBroker", "[MessageBroker] Запущен мониторинг очередей");
        
        while (isRunning) {
            try {
                Thread.sleep(50);
                Logger.debug("MessageBroker", String.format("[Мониторинг] Очереди: входящая=%d, исходящая=%d, аналитика=%d",
                    incomingQueue.size(), outgoingQueue.size(), analyticsQueue.size()));
            } catch (InterruptedException e) {
                break;
            }
        }
        
        Logger.info("MessageBroker", "[MessageBroker] Мониторинг остановлен");
    }
    
    /**
     * Graceful shutdown
     */
    public void shutdown() {
        Logger.info("MessageBroker", "[MessageBroker] Остановка...");
        isRunning = false;
        int analyticsSize = analyticsQueue.size();
        if (analyticsSize > 0) {
            Logger.info("MessageBroker", String.format("[MessageBroker] Очищаем analytics очередь: %d сообщений", analyticsSize));
            analyticsQueue.clear();
        }
        // Останавливаем scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        // Останавливаем executor
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        Logger.info("MessageBroker", "MessageBroker остановлен");
    }
    
    // Геттеры для доступа к очередям из других компонентов
    public BlockingQueue<ChatMessage> getIncomingQueue() {
        return incomingQueue;
    }
    
    public BlockingQueue<ChatMessage> getOutgoingQueue() {
        return outgoingQueue;
    }
    
    public BlockingQueue<ChatMessage> getAnalyticsQueue() {
        return analyticsQueue;
    }
}
