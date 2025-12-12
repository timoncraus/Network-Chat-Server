import common.ChatMessage;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MessageBroker implements Runnable {
    private final ChatServer server;
    private final BlockingQueue<ChatMessage> incomingQueue;
    private final BlockingQueue<ChatMessage> outgoingQueue;
    private final BlockingQueue<ChatMessage> analyticsQueue;
    private final ClientManager clientManager;
    private final ExecutorService executor;
    private volatile boolean isRunning;

    public MessageBroker(ChatServer server) {
        this.server = server;
        this.incomingQueue = new LinkedBlockingQueue<>();
        this.outgoingQueue = new LinkedBlockingQueue<>();
        this.analyticsQueue = new LinkedBlockingQueue<>();
        this.clientManager = new ClientManager();
        this.executor = Executors.newFixedThreadPool(3); // Потоки для обработки очередей
        this.isRunning = true;
    }

    @Override
    public void run() {
        System.out.println("MessageBroker запущен");
        
        // Запускаем потоки-обработчики
        executor.execute(this::processIncomingMessages);
        executor.execute(this::processOutgoingMessages);
        executor.execute(this::processAnalyticsMessages);
        
        // Поток для мониторинга
        new Thread(this::monitorQueues, "QueueMonitor").start();
    }

    // Метод для клиентов (Producer)
    public void processIncomingMessage(ChatMessage message) {
        try {
            incomingQueue.put(message);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Ошибка при добавлении сообщения в очередь: " + e.getMessage());
        }
    }

    // Поток-маршрутизатор (Consumer для incomingQueue)
    private void processIncomingMessages() {
        while (isRunning) {
            try {
                ChatMessage message = incomingQueue.poll(100, TimeUnit.MILLISECONDS);
                if (message == null) continue;

                // Маршрутизация по типу сообщения
                switch (message.getType()) {
                    case USER_MESSAGE:
                        // Отправляем всем клиентам
                        outgoingQueue.put(message);
                        // И боту для анализа
                        analyticsQueue.put(message);
                        break;
                        
                    case COMMAND:
                        // Команды идут напрямую в analyticsQueue для обработки ботом
                        analyticsQueue.put(message);
                        break;
                        
                    case SYSTEM_MESSAGE:
                    case STATISTICS:
                        // Системные сообщения и статистика идут всем клиентам
                        outgoingQueue.put(message);
                        break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // Поток-отправитель (Consumer для outgoingQueue)
    private void processOutgoingMessages() {
        while (isRunning) {
            try {
                ChatMessage message = outgoingQueue.poll(100, TimeUnit.MILLISECONDS);
                if (message == null) continue;
                
                // Отправляем сообщение всем клиентам через сервер
                server.broadcastMessage(message);
                
            } catch (Exception e) {
                System.err.println("Ошибка при отправке сообщения: " + e.getMessage());
            }
        }
    }

    // Поток для аналитики (Consumer для analyticsQueue)
    private void processAnalyticsMessages() {
        while (isRunning) {
            try {
                ChatMessage message = analyticsQueue.poll(100, TimeUnit.MILLISECONDS);
                if (message == null) continue;
                
                // Здесь будет вызываться AnalyticsBot
                // Пока просто логируем
                System.out.println("[Analytics Queue] Получено сообщение для анализа: " + message);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // Мониторинг очередей
    private void monitorQueues() {
        while (isRunning) {
            try {
                Thread.sleep(5000);
                System.out.printf("[Мониторинг] Очереди: входящая=%d, исходящая=%d, аналитика=%d%n",
                    incomingQueue.size(), outgoingQueue.size(), analyticsQueue.size());
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    // Методы для управления клиентами
    public void addClient(String username) {
        clientManager.addClient(username);
    }
    
    public void removeClient(String username) {
        clientManager.removeClient(username);
    }
    
    public String[] getActiveUsers() {
        return clientManager.getActiveUsers();
    }

    public void shutdown() {
        isRunning = false;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        System.out.println("MessageBroker остановлен");
    }
}