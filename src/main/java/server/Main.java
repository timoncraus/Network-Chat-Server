package server;

public class Main {
    private static ChatServer server;
    private static AnalyticsBot analyticsBot;
    
    public static void main(String[] args) {
        try {
            ServerConfig config = ServerConfig.getInstance();
            
            // Установка уровня логирования из конфигурации
            Logger.setLogLevel(Logger.LogLevel.valueOf(config.getLogLevel()));
            
            Logger.info("=".repeat(50));
            Logger.info("Запуск " + config.getServerName());
            Logger.info("Порт: " + config.getPort() + ", Максимум клиентов: " + config.getMaxClients());
            Logger.info("=".repeat(50));
            
            // Создаем сервер с использованием конфигурации
            server = new ChatServer(config.getPort(), config.getMaxClients());
            
            // Получаем MessageBroker из сервера
            MessageBroker messageBroker = server.getMessageBroker();
            
            // Создаем и запускаем AnalyticsBot (если включен)
            if (config.isAnalyticsEnabled()) {
                analyticsBot = new AnalyticsBot(messageBroker);
                analyticsBot.start();
                Logger.info("AnalyticsBot запущен");
            } else {
                Logger.info("AnalyticsBot отключен в конфигурации");
            }
            
            // Запускаем PerformanceMonitor
            PerformanceMonitor monitor = new PerformanceMonitor(server, messageBroker);
            monitor.start();
            Logger.info("PerformanceMonitor запущен");
            
            // Запускаем сервер в отдельном потоке
            Thread serverThread = new Thread(() -> server.start(), "ChatServer-MainThread");
            serverThread.setDaemon(false); // Поток не является демоном, чтобы приложение не завершилось
            serverThread.start();
            Logger.info("Сервер запущен и слушает порт " + config.getPort());
            
            // Ожидаем завершения
            serverThread.join();
            
        } catch (Exception e) {
            Logger.error("Критическая ошибка при запуске сервера", e);
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