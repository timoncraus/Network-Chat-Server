package server;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public enum LogLevel {
        DEBUG, INFO, WARN, ERROR
    }
    
    public static void log(LogLevel level, String message) {
        String timestamp = LocalDateTime.now().format(formatter);
        String threadName = Thread.currentThread().getName();
        System.out.printf("[%s] [%s] %s: %s%n", timestamp, threadName, level, message);
    }
    
    public static void debug(String message) {
        log(LogLevel.DEBUG, message);
    }
    
    public static void info(String message) {
        log(LogLevel.INFO, message);
    }
    
    public static void warn(String message) {
        log(LogLevel.WARN, message);
    }
    
    public static void warn(String message, Throwable throwable) {
        log(LogLevel.WARN, message + " - " + throwable.getMessage());
        if (throwable != null) {
            throwable.printStackTrace();
        }
    }
    
    public static void error(String message) {
        log(LogLevel.ERROR, message);
    }
    
    public static void error(String message, Throwable throwable) {
        log(LogLevel.ERROR, message + " - " + throwable.getMessage());
        throwable.printStackTrace();
    }
    
    // Улучшенные методы логирования с контекстом
    public static void error(String context, String message) {
        log(LogLevel.ERROR, String.format("[%s] %s", context, message));
    }
    
    public static void error(String context, String message, Throwable throwable) {
        log(LogLevel.ERROR, String.format("[%s] %s - %s", context, message, throwable.getMessage()));
        if (throwable != null) {
            throwable.printStackTrace();
        }
    }
    
    public static void warn(String context, String message) {
        log(LogLevel.WARN, String.format("[%s] %s", context, message));
    }
    
    public static void info(String context, String message) {
        log(LogLevel.INFO, String.format("[%s] %s", context, message));
    }
}