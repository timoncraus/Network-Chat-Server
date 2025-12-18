package server;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class ClientManager {
    // Карта для хранения информации о клиентах
    private final Map<String, ClientInfo> clients = new ConcurrentHashMap<>();
    
    // Набор для хранения активных пользователей
    private final Set<String> activeUsers = new CopyOnWriteArraySet<>();
    
    // Время жизни неактивного пользователя (в миллисекундах) - 5 минут
    private static final long INACTIVE_TIMEOUT = 300000; // 5 минут

    /**
     * Информация о клиенте
     */
    private static class ClientInfo {
        private final String username;
        private volatile long lastActivityTime;
        
        public ClientInfo(String username) {
            this.username = username;
            this.lastActivityTime = System.currentTimeMillis();
        }
        
        public void updateActivity() {
            this.lastActivityTime = System.currentTimeMillis();
        }
        
        public long getLastActivityTime() {
            return lastActivityTime;
        }
        
        public String getUsername() {
            return username;
        }
    }

    /**
     * Добавить нового клиента
     */
    public void addClient(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Имя пользователя не может быть пустым");
        }
        
        ClientInfo clientInfo = new ClientInfo(username);
        clients.put(username, clientInfo);
        activeUsers.add(username);
    }

    /**
     * Удалить клиента
     */
    public void removeClient(String username) {
        if (username != null) {
            clients.remove(username);
            activeUsers.remove(username);
        }
    }

    /**
     * Обновить время активности клиента
     */
    public void updateActivity(String username) {
        if (username != null) {
            ClientInfo clientInfo = clients.get(username);
            if (clientInfo != null) {
                clientInfo.updateActivity();
            }
        }
    }

    /**
     * Получить список активных пользователей
     */
    public String[] getActiveUsers() {
        return activeUsers.toArray(new String[0]);
    }

    /**
     * Проверить, существует ли пользователь
     */
    public boolean hasClient(String username) {
        return username != null && clients.containsKey(username);
    }

    /**
     * Получить время последней активности пользователя
     */
    public long getLastActivityTime(String username) {
        if (username == null) {
            return 0;
        }
        
        ClientInfo clientInfo = clients.get(username);
        return clientInfo != null ? clientInfo.getLastActivityTime() : 0;
    }

    /**
     * Очистить неактивных пользователей
     */
    public void cleanupInactiveUsers(long timeoutMs) {
        long currentTime = System.currentTimeMillis();
        Set<String> usersToRemove = new CopyOnWriteArraySet<>();
        
        for (Map.Entry<String, ClientInfo> entry : clients.entrySet()) {
            String username = entry.getKey();
            ClientInfo clientInfo = entry.getValue();
            
            if (currentTime - clientInfo.getLastActivityTime() > timeoutMs) {
                usersToRemove.add(username);
            }
        }
        
        for (String username : usersToRemove) {
            removeClient(username);
            Logger.info("ClientManager", "Удален неактивный пользователь: " + username);
        }
    }

    /**
     * Получить общее количество подключенных клиентов
     */
    public int getClientCount() {
        return clients.size();
    }

    /**
     * Проверить, активен ли пользователь
     */
    public boolean isUserActive(String username) {
        return username != null && activeUsers.contains(username);
    }
}
