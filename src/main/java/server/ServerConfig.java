package server;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ServerConfig {
    private static ServerConfig instance;
    private final Properties properties = new Properties();
    
    // Параметры по умолчанию
    private static final int DEFAULT_PORT = 12345;
    private static final int DEFAULT_MAX_CLIENTS = 100;
    private static final int DEFAULT_MESSAGE_QUEUE_SIZE = 1000;
    private static final int DEFAULT_THREAD_POOL_SIZE = 50;
    private static final long DEFAULT_CLIENT_TIMEOUT = 300000; // 5 минут
    private static final boolean DEFAULT_LOGGING_ENABLED = true;
    private static final int DEFAULT_REPORT_INTERVAL_MINUTES = 1;
    private static final int DEFAULT_MONITOR_INTERVAL_SECONDS = 5;
    
    private ServerConfig() {
        loadConfiguration();
    }
    
    public static ServerConfig getInstance() {
        if (instance == null) {
            synchronized (ServerConfig.class) {
                if (instance == null) {
                    instance = new ServerConfig();
                }
            }
        }
        return instance;
    }
    
    private void loadConfiguration() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("server.properties")) {
            if (input != null) {
                properties.load(input);
            } else {
                Logger.warn("Файл конфигурации server.properties не найден, используются значения по умолчанию");
            }
        } catch (IOException e) {
            Logger.warn("Ошибка при загрузке конфигурации, используются значения по умолчанию");
            Logger.error("IOException при загрузке конфигурации", e);
        }
    }
    
    public int getPort() {
        return Integer.parseInt(properties.getProperty("server.port", String.valueOf(DEFAULT_PORT)));
    }
    
    public int getMaxClients() {
        return Integer.parseInt(properties.getProperty("server.max.clients", String.valueOf(DEFAULT_MAX_CLIENTS)));
    }
    
    public int getMessageQueueSize() {
        return Integer.parseInt(properties.getProperty("server.message.queue.size", String.valueOf(DEFAULT_MESSAGE_QUEUE_SIZE)));
    }
    
    public int getThreadPoolSize() {
        return Integer.parseInt(properties.getProperty("server.thread.pool.size", String.valueOf(DEFAULT_THREAD_POOL_SIZE)));
    }
    
    public long getClientTimeout() {
        return Long.parseLong(properties.getProperty("server.client.timeout", String.valueOf(DEFAULT_CLIENT_TIMEOUT)));
    }
    
    public boolean isLoggingEnabled() {
        return Boolean.parseBoolean(properties.getProperty("server.logging.enabled", String.valueOf(DEFAULT_LOGGING_ENABLED)));
    }
    
    public int getReportIntervalMinutes() {
        return Integer.parseInt(properties.getProperty("server.report.interval.minutes", String.valueOf(DEFAULT_REPORT_INTERVAL_MINUTES)));
    }
    
    public int getMonitorIntervalSeconds() {
        return Integer.parseInt(properties.getProperty("server.monitor.interval.seconds", String.valueOf(DEFAULT_MONITOR_INTERVAL_SECONDS)));
    }
    
    public String getServerName() {
        return properties.getProperty("server.name", "NetChat Server");
    }
    
    public boolean isAnalyticsEnabled() {
        return Boolean.parseBoolean(properties.getProperty("server.analytics.enabled", "true"));
    }
    
    public boolean isCommandProcessorEnabled() {
        return Boolean.parseBoolean(properties.getProperty("server.command.processor.enabled", "true"));
    }
    
    public String getLogLevel() {
        return properties.getProperty("server.logging.level", "INFO");
    }
}