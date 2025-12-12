import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class ClientManager {
    private final Set<String> activeClients;
    private final ConcurrentHashMap<String, Long> clientActivity;

    public ClientManager() {
        this.activeClients = new CopyOnWriteArraySet<>();
        this.clientActivity = new ConcurrentHashMap<>();
    }

    public void addClient(String username) {
        activeClients.add(username);
        updateActivity(username);
    }

    public void removeClient(String username) {
        activeClients.remove(username);
        clientActivity.remove(username);
    }

    public void updateActivity(String username) {
        clientActivity.put(username, System.currentTimeMillis());
    }

    public String[] getActiveUsers() {
        return activeClients.toArray(new String[0]);
    }

    public int getActiveCount() {
        return activeClients.size();
    }

    public boolean isUserActive(String username) {
        return activeClients.contains(username);
    }

    public long getLastActivity(String username) {
        return clientActivity.getOrDefault(username, 0L);
    }

    public void broadcastToAll(String message) {
        // Здесь можно добавить логику для отправки сообщений
        // непосредственно через ClientManager, если нужно
    }
}