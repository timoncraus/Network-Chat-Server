package server;

import common.ChatMessage;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong; 

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
        System.out.println("[MessageBroker] Запуск MessageBroker...");
        
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
            System.err.println("[MessageBroker] Прервано при добавлении сообщения: " + 
                message.getText());
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
                // Ждем сообщение не более 100мс
                ChatMessage message = incomingQueue.poll(100, TimeUnit.MILLISECONDS);
                if (message == null) continue;
                
                // Логируем для отладки
                System.out.printf("[Маршрутизатор] Получено: [%s] %s: %s%n",
                    message.getType(), message.getUser(), 
                    message.getText().length() > 30 ? 
                    message.getText().substring(0, 30) + "..." : message.getText());
                
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
                System.err.println("[Маршрутизатор] Ошибка: " + e.getMessage());
                e.printStackTrace();
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
                // Ждем сообщение
                ChatMessage message = outgoingQueue.poll(100, TimeUnit.MILLISECONDS);
                if (message == null) continue;
                
                // Логируем
                System.out.printf("[Отправитель] Отправляю: [%s] %s%n",
                    message.getType(), message.getUser());
                
                // Отправляем сообщение через сервер (broadcast)
                server.broadcastMessage(message);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("[Отправитель] Поток прерван");
                break;
            } catch (Exception e) {
                System.err.println("[Отправитель] Ошибка при отправке: " + e.getMessage());
            }
        }
        
        System.out.println("[MessageBroker] Поток отправителя остановлен");
    }
    
    /**
     * Поток 3: ДЛЯ АНАЛИТИКИ
     * Просто передает сообщения в analyticsQueue (бот заберет сам)
     */
    // В методе processAnalyticsMessages замените sleep на обработку:
private void processAnalyticsMessages() {
    System.out.println("[MessageBroker] Запущен поток для аналитики");
    
    while (isRunning) {
        try {
            // Вместо Thread.sleep(1000) добавьте:
            ChatMessage message = analyticsQueue.poll(100, TimeUnit.MILLISECONDS);
            if (message != null) {
                // Здесь будет обработка ботом
                // Пока просто логируем
                System.out.println("[Analytics] Получено: " + message.getUser());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
        }
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
                Thread.sleep(5000); // Каждые 5 секунд
                
                System.out.printf("[Мониторинг] Очереди: входящая=%d, исходящая=%d, аналитика=%d | Клиенты: %d%n",
                    incomingQueue.size(), outgoingQueue.size(), analyticsQueue.size(),
                    clientManager.getActiveCount());
                
                // Предупреждение, если очередь переполняется
                if (incomingQueue.size() > 800) {
                    System.err.println("[Мониторинг] ⚠️  Входящая очередь почти полна!");
                }
                
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
        
        // Очищаем очереди
        incomingQueue.clear();
        outgoingQueue.clear();
        analyticsQueue.clear();
        
        System.out.println("[MessageBroker] Остановлен. Обработано сообщений: " + 
            messagesProcessed.get());
    }
    
    // ========== ДОПОЛНИТЕЛЬНЫЕ МЕТОДЫ ==========
    
    /**
     * Отправить приватное сообщение (доп. функция)
     */
    public boolean sendPrivateMessage(String fromUser, String toUser, String text) {
        if (!clientManager.isUserActive(toUser)) {
            return false;
        }
        
        ChatMessage privateMsg = new ChatMessage(
            ChatMessage.MessageType.USER_MESSAGE,
            fromUser,
            "[Приватно для " + toUser + "] " + text
        );
        
        // Здесь нужно было бы найти конкретного клиента и отправить ему
        // Пока просто отправляем в общую очередь
        try {
            outgoingQueue.put(privateMsg);
            return true;
        } catch (InterruptedException e) {
            return false;
        }
    }
    
    /**
     * Получить статистику брокера (для мониторинга)
     */
    public String getBrokerStats() {
        return String.format(
            "MessageBroker статистика:%n" +
            "  Обработано сообщений: %d%n" +
            "  Активных клиентов: %d%n" +
            "  Размеры очередей: входящая=%d, исходящая=%d, аналитика=%d%n" +
            "  Статус: %s",
            messagesProcessed.get(),
            clientManager.getActiveCount(),
            incomingQueue.size(), outgoingQueue.size(), analyticsQueue.size(),
            isRunning ? "РАБОТАЕТ" : "ОСТАНОВЛЕН"
        );
    }
}
