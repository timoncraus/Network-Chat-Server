package test;

import server.MessageBroker;
import server.ChatServer;  // Добавьте этот импорт
import common.ChatMessage;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;  // Добавьте этот импорт

public class PerformanceTest {
    public static void main(String[] args) throws Exception {
        System.out.println("=== ТЕСТ ПРОИЗВОДИТЕЛЬНОСТИ ===");
        
        // Mock сервер должен наследовать ChatServer
        PerformanceTestServer server = new PerformanceTestServer();
        MessageBroker broker = new MessageBroker(server);
        
        // Запускаем брокер
        Thread brokerThread = new Thread(broker);
        brokerThread.start();
        Thread.sleep(2000);
        
        // Отправляем 100 сообщений
        int totalMessages = 100;
        long startTime = System.currentTimeMillis();
        
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(totalMessages);
        
        for (int i = 0; i < totalMessages; i++) {
            final int msgNum = i;
            executor.submit(() -> {
                broker.processIncomingMessage(
                    new ChatMessage(ChatMessage.MessageType.USER_MESSAGE,
                                   "TestUser" + (msgNum % 5),
                                   "Тестовое сообщение #" + msgNum));
                latch.countDown();
            });
            
            if (msgNum % 20 == 0) Thread.sleep(10);
        }
        
        latch.await();
        long endTime = System.currentTimeMillis();
        
        // Статистика
        long duration = endTime - startTime;
        double msgPerSecond = totalMessages / (duration / 1000.0);
        
        System.out.println("\nРезультаты:");
        System.out.println("  Сообщений: " + totalMessages);
        System.out.println("  Время: " + duration + " мс");
        System.out.println("  Сообщений/сек: " + String.format("%.1f", msgPerSecond));
        System.out.println("  Broadcast выполнено: " + server.getBroadcastCount());
        
        // Ожидаем обработки
        Thread.sleep(2000);
        System.out.println("  Всего обработано: " + server.getBroadcastCount());
        
        broker.shutdown();
        executor.shutdown();
        
        System.out.println("\n" + (msgPerSecond > 50 ? "✅ ТЕСТ ПРОЙДЕН" : "⚠️  СЛИШКОМ МЕДЛЕННО"));
    }
    
    // PerformanceTestServer должен наследовать ChatServer
    static class PerformanceTestServer extends ChatServer {
        private AtomicInteger broadcastCount = new AtomicInteger(0);
        
        public PerformanceTestServer() {
            super(12345, 10); // Вызываем конструктор родителя
        }
        
        @Override
        public void broadcastMessage(ChatMessage msg) {
            broadcastCount.incrementAndGet();
            // Для теста просто считаем, не отправляем реальным клиентам
            if (broadcastCount.get() % 20 == 0) {
                System.out.println("    [Broadcast #" + broadcastCount.get() + "] " + 
                    msg.getUser() + ": " + 
                    (msg.getText().length() > 20 ? msg.getText().substring(0, 20) + "..." : msg.getText()));
            }
        }
        
        // Переопределяем start, чтобы не запускать реальный сервер
        @Override
        public void start() {
            // Не делаем ничего в тесте
        }
        
        @Override
        public void shutdown() {
            // Не делаем ничего в тесте
        }
        
        public int getBroadcastCount() { 
            return broadcastCount.get(); 
        }
    }
}
