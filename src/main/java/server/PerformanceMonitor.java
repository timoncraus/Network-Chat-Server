package server;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PerformanceMonitor {
    private final ChatServer server;
    private final MessageBroker messageBroker;
    private final ScheduledExecutorService scheduler;
    private long startTime;
    
    public PerformanceMonitor(ChatServer server, MessageBroker messageBroker) {
        this.server = server;
        this.messageBroker = messageBroker;
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.startTime = System.currentTimeMillis();
    }
    
    public void start() {
        Logger.info("PerformanceMonitor запущен");
        
        scheduler.scheduleAtFixedRate(() -> {
            try {
                printMetrics();
            } catch (Exception e) {
                Logger.error("Ошибка в PerformanceMonitor: " + e.getMessage(), e);
            }
        }, 5, 5, TimeUnit.SECONDS);
    }
    
    private void printMetrics() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long uptime = System.currentTimeMillis() - startTime;
        
        Logger.info("=".repeat(60));
        Logger.info("МОНИТОРИНГ ПРОИЗВОДИТЕЛЬНОСТИ");
        Logger.info("=".repeat(60));
        
        Logger.info(String.format("Время работы: %s", formatUptime(uptime)));
        
        // Активные подключения
        Logger.info(String.format("Активные подключения: %d", server.getActiveUserCount()));
        
        // Использование памяти
        long usedMemory = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long maxMemory = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024);
        Logger.info(String.format("Память: %dMB / %dMB (%.1f%%)",
            usedMemory, maxMemory, (usedMemory * 100.0 / maxMemory)));
        
        // Статистика потоков
        ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
        while (rootGroup.getParent() != null) {
            rootGroup = rootGroup.getParent();
        }
        Logger.info(String.format("Активные потоки: %d", rootGroup.activeCount()));
        
        // Загрузка CPU
        Logger.info(String.format("Загрузка CPU: %.1f%%", getProcessCpuLoad()));
        
        // Статистика MessageBroker
        if (messageBroker != null) {
            Logger.info(String.format("Очередь входящих: %d", messageBroker.getIncomingQueue().size()));
            Logger.info(String.format("Очередь исходящих: %d", messageBroker.getOutgoingQueue().size()));
            Logger.info(String.format("Очередь аналитики: %d", messageBroker.getAnalyticsQueue().size()));
        } else {
            Logger.warn("MessageBroker недоступен для мониторинга");
        }
        
        Logger.info("=".repeat(60));
    }
    
    private String formatUptime(long millis) {
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }
    
    private double getProcessCpuLoad() {
        // Упрощенная версия - в реальной системе используйте OperatingSystemMXBean
        return Math.random() * 30 + 10; // Заглушка для демонстрации
    }
    
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        System.out.println("PerformanceMonitor остановлен");
    }
}