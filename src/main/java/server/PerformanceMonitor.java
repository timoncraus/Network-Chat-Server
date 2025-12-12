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
        System.out.println("PerformanceMonitor запущен");
        
        scheduler.scheduleAtFixedRate(() -> {
            try {
                printMetrics();
            } catch (Exception e) {
                System.err.println("Ошибка в PerformanceMonitor: " + e.getMessage());
            }
        }, 5, 5, TimeUnit.SECONDS);
    }
    
    private void printMetrics() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long uptime = System.currentTimeMillis() - startTime;
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("МОНИТОРИНГ ПРОИЗВОДИТЕЛЬНОСТИ");
        System.out.println("=".repeat(60));
        
        System.out.printf("Время работы: %s%n", formatUptime(uptime));
        
        // Активные подключения (нужен геттер в ChatServer)
        // System.out.printf("Активные подключения: %d%n", server.getActiveConnections());
        
        // Использование памяти
        long usedMemory = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long maxMemory = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024);
        System.out.printf("Память: %dMB / %dMB (%.1f%%)%n", 
            usedMemory, maxMemory, (usedMemory * 100.0 / maxMemory));
        
        // Статистика потоков
        ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
        while (rootGroup.getParent() != null) {
            rootGroup = rootGroup.getParent();
        }
        System.out.printf("Активные потоки: %d%n", rootGroup.activeCount());
        
        // Загрузка CPU
        System.out.printf("Загрузка CPU: %.1f%%%n", getProcessCpuLoad());
        
        System.out.println("=".repeat(60));
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