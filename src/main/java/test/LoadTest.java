package test;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadTest {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 12345;
    private static final int NUM_CLIENTS = 50;
    private static final int MESSAGES_PER_CLIENT = 100;
    private static final AtomicInteger successfulConnections = new AtomicInteger(0);
    private static final AtomicInteger messagesSent = new AtomicInteger(0);
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("🚀 Начало нагрузочного тестирования");
        System.out.println("Клиентов: " + NUM_CLIENTS);
        System.out.println("Сообщений на клиента: " + MESSAGES_PER_CLIENT);
        System.out.println("Ожидаемое общее количество сообщений: " + (NUM_CLIENTS * MESSAGES_PER_CLIENT));
        
        ExecutorService executor = Executors.newFixedThreadPool(NUM_CLIENTS);
        CountDownLatch latch = new CountDownLatch(NUM_CLIENTS);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < NUM_CLIENTS; i++) {
            final int clientId = i;
            executor.execute(() -> {
                try {
                    simulateClient("TestUser_" + clientId, latch);
                    successfulConnections.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("Клиент " + clientId + " ошибка: " + e.getMessage());
                }
            });
        }
        
        // Ожидаем завершения всех клиентов
        latch.await();
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        
        System.out.println("\n" + "=".repeat(50));
        System.out.println("РЕЗУЛЬТАТЫ ТЕСТИРОВАНИЯ");
        System.out.println("=".repeat(50));
        System.out.println("Успешных подключений: " + successfulConnections.get() + "/" + NUM_CLIENTS);
        System.out.println("Отправлено сообщений: " + messagesSent.get());
        System.out.println("Общее время: " + duration + " мс");
        System.out.println("Среднее время на сообщение: " + 
            (duration / Math.max(1, messagesSent.get())) + " мс");
        System.out.println("Сообщений в секунду: " + 
            (messagesSent.get() * 1000.0 / Math.max(1, duration)));
        
        if (successfulConnections.get() == NUM_CLIENTS) {
            System.out.println("✅ Тест пройден успешно!");
        } else {
            System.out.println("❌ Тест не пройден!");
        }
    }
    
    private static void simulateClient(String username, CountDownLatch latch) {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            
            // Читаем приглашение
            String response = in.readLine();
            System.out.println(username + ": " + response);
            
            // Отправляем имя пользователя
            out.println(username);
            
            // Читаем приветствие
            response = in.readLine();
            System.out.println(username + ": " + response);
            
            // Отправляем тестовые сообщения
            for (int i = 0; i < MESSAGES_PER_CLIENT; i++) {
                String message = "Тестовое сообщение #" + i + " от " + username;
                out.println(message);
                messagesSent.incrementAndGet();
                
                // Читаем ответ (можно закомментировать для скорости)
                // response = in.readLine();
                
                // Небольшая пауза между сообщениями
                Thread.sleep(10);
            }
            
            // Отправляем команду
            out.println("/stats");
            
            // Отключаемся
            out.println("/exit");
            
        } catch (Exception e) {
            System.err.println("Ошибка в клиенте " + username + ": " + e.getMessage());
        } finally {
            latch.countDown();
        }
    }
}