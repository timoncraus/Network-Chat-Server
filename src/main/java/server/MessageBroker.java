package server;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

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
        this.isRunning = true;
        
        System.out.println("[MessageBroker] Инициализирован");
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
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
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
            System.err.println("[MessageBroker] Не принимаю сообщения, брокер остановлен");
            return;
        }
        
        try {
            // Обновляем активность пользователя
            clientManager.updateActivity(message.getUser());
            
            // Кладем сообщение во входящую очередь
            incomingQueue.put(message);
            
            // Статистика
            if (messagesProcessed.incrementAndGet() % 100 == 0) {
                System.out.printf("[MessageBroker] Обработано %d сообщений%n", 
                    messagesProcessed.get());
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
<<<<<<< HEAD
            System.err.println("[MessageBroker] Прервано при добавлении сообщения: " + 
                message.getText());
=======
            Logger.error("MessageBroker", "Прервано прерыванием при добавлении сообщения в очередь", e);
>>>>>>> 9451f4f (Обновление README)
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
    
    /**
     * Получить очередь для аналитики (для AnalyticsBot)
     */
    public BlockingQueue<ChatMessage> getAnalyticsQueue() {
        return analyticsQueue;
    }
    
    /**
     * Получить очередь для исходящих сообщений
     */
    public BlockingQueue<ChatMessage> getOutgoingQueue() {
        return outgoingQueue;
    }
    
    // ========== PRIVATE МЕТОДЫ ОБРАБОТКИ ==========
    
    /**
     * Поток 1: МАРШРУТИЗАТОР
     * Берет сообщения из incomingQueue и распределяет по другим очередям
     */
    private void processIncomingMessages() {
        System.out.println("[MessageBroker] Запущен поток маршрутизатора");
        
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
                        System.err.println("[Маршрутизатор] Неизвестный тип сообщения: " + 
                            message.getType());
                        break;
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("[Маршрутизатор] Поток прерван");
                break;
            } catch (Exception e) {
                Logger.error("MessageBroker", "Ошибка при маршрутизации сообщения: " + e.getMessage(), e);
            }
        }
        
        System.out.println("[MessageBroker] Поток маршрутизатора остановлен");
    }
    
    /**
     * Поток 2: ОТПРАВИТЕЛЬ
     * Берет сообщения из outgoingQueue и отправляет всем клиентам
     */
    private void processOutgoingMessages() {
        System.out.println("[MessageBroker] Запущен поток отправителя");
        
        while (isRunning) {
            try {
                ChatMessage message = outgoingQueue.take();
                
                // Логируем
                System.out.printf("[Отправитель] Отправляю: [%s] %s%n",
                    message.getType(), message.getUser());
                
                // Отправляем сообщение через сервер (broadcast)
                server.broadcastMessage(message);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                Logger.error("MessageBroker", "Ошибка при отправке сообщения: " + e.getMessage(), e);
            }
        }
        
        System.out.println("[MessageBroker] Поток отправителя остановлен");
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
<<<<<<< HEAD
        while (isRunning) {
            try {
                ChatMessage message = analyticsQueue.take();
                
                // Здесь будет вызываться AnalyticsBot
                // Пока просто логируем
                Logger.info("MessageBroker", "[Analytics Queue] Получено сообщение для анализа: " + message);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
        }
=======
        // В текущей архитектуре этот поток не нужен, так как AnalyticsBot сам извлекает сообщения из очереди
        // Оставляем пустой метод для совместимости
        Logger.info("MessageBroker", "Поток аналитики запущен (заглушка для совместимости)");
>>>>>>> 9451f4f (Обновление README)
    }
    
    System.out.println("[MessageBroker] Поток аналитики остановлен");
}
    
    /**
     * Мониторинг очередей (отдельный поток)
     */
    private void monitorQueues() {
        System.out.println("[MessageBroker] Запущен мониторинг очередей");
        
        while (isRunning) {
            try {
                Thread.sleep(5000);
                Logger.info("MessageBroker", String.format("[Мониторинг] Очереди: входящая=%d, исходящая=%d, аналитика=%d",
                    incomingQueue.size(), outgoingQueue.size(), analyticsQueue.size()));
            } catch (InterruptedException e) {
                break;
            }
        }
        
        System.out.println("[MessageBroker] Мониторинг остановлен");
    }
    
    /**
     * Graceful shutdown
     */
    public void shutdown() {
        System.out.println("[MessageBroker] Остановка...");
        isRunning = false;
        int analyticsSize = analyticsQueue.size();
    if (analyticsSize > 0) {
        System.out.println("[MessageBroker] Очищаем analytics очередь: " + analyticsSize + " сообщений");
        analyticsQueue.clear();
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
