import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Main {
    private static ChatServer server;
    private static AnalyticsBot analyticsBot;
    
    public static void main(String[] args) {
        try {
            System.out.println("=".repeat(50));
            System.out.println("Запуск многопоточного чат-сервера с ботом-аналитиком");
            System.out.println("=".repeat(50));
            
            // Создаем сервер
            server = new ChatServer(12345, 100);
            
            // Получаем MessageBroker из сервера
            MessageBroker messageBroker = server.getMessageBroker();
            
            // Создаем и запускаем AnalyticsBot
            analyticsBot = new AnalyticsBot(messageBroker);
            analyticsBot.start();
            
            // Запускаем PerformanceMonitor
            PerformanceMonitor monitor = new PerformanceMonitor(server, messageBroker);
            monitor.start();
            
            // Запускаем сервер в отдельном потоке
            Thread serverThread = new Thread(() -> server.start());
            serverThread.start();
            
            // Ожидаем завершения
            serverThread.join();
            
        } catch (Exception e) {
            System.err.println("Критическая ошибка: " + e.getMessage());
            e.printStackTrace();
            shutdown();
        }
    }
    
    public static void shutdown() {
        System.out.println("\nИнициировано завершение работы...");
        if (analyticsBot != null) {
            analyticsBot.shutdown();
        }
        if (server != null) {
            server.shutdown();
        }
    }
    
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(Main::shutdown));
    }
}