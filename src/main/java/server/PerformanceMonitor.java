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
        Logger.info("PerformanceMonitor", "PerformanceMonitor запущен");
        
        scheduler.scheduleAtFixedRate(() -> {
            try {
                printMetrics();
            } catch (Exception e) {
                Logger.error("PerformanceMonitor", "Ошибка в PerformanceMonitor: " + e.getMessage(), e);
            }
        }, 5, ServerConfig.getInstance().getMonitorIntervalSeconds(), TimeUnit.SECONDS);
    }
    
    private void printMetrics() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long uptime = System.currentTimeMillis() - startTime;
        
        Logger.debug("PerformanceMonitor", "=".repeat(60));
        Logger.debug("PerformanceMonitor", "МОНИТОРИНГ ПРОИЗВОДИТЕЛЬНОСТИ");
        Logger.debug("PerformanceMonitor", "=".repeat(60));
        
        Logger.debug("PerformanceMonitor", String.format("Время работы: %s", formatUptime(uptime)));
        
        // Активные подключения
        Logger.debug("PerformanceMonitor", String.format("Активные подключения: %d", server.getActiveUserCount()));
        
        // Использование памяти
        long usedMemory = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long maxMemory = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024);
        Logger.debug("PerformanceMonitor", String.format("Память: %dMB / %dMB (%.1f%%)",
            usedMemory, maxMemory, (usedMemory * 100.0 / maxMemory)));
        
        // Статистика потоков
        ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
        while (rootGroup.getParent() != null) {
            rootGroup = rootGroup.getParent();
        }
        Logger.debug("PerformanceMonitor", String.format("Активные потоки: %d", rootGroup.activeCount()));
        
        // Загрузка CPU
        Logger.debug("PerformanceMonitor", String.format("Загрузка CPU: %.1f%%", getProcessCpuLoad()));
        
        // Статистика MessageBroker
        if (messageBroker != null) {
            Logger.debug("PerformanceMonitor", String.format("Очередь входящих: %d", messageBroker.getIncomingQueue().size()));
            Logger.debug("PerformanceMonitor", String.format("Очередь исходящих: %d", messageBroker.getOutgoingQueue().size()));
            Logger.debug("PerformanceMonitor", String.format("Очередь аналитики: %d", messageBroker.getAnalyticsQueue().size()));
        } else {
            Logger.warn("PerformanceMonitor", "MessageBroker недоступен для мониторинга");
        }
        
        Logger.debug("PerformanceMonitor", "=".repeat(60));
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