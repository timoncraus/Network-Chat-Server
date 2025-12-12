import common.ChatMessage;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ChatServer {
    private final int port;
    private final ExecutorService clientThreadPool;
    private final ConcurrentHashMap<String, ClientHandler> connectedClients;
    private volatile boolean isRunning;
    private MessageBroker messageBroker; // Ссылка на брокер

    public ChatServer(int port, int maxClients) {
        this.port = port;
        this.clientThreadPool = Executors.newFixedThreadPool(maxClients);
        this.connectedClients = new ConcurrentHashMap<>();
        this.isRunning = true;
        this.messageBroker = new MessageBroker(this); // Создаем брокер
    }

    public void start() {
        System.out.println("Запуск чат-сервера на порту " + port);
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            // Запускаем поток для MessageBroker
            new Thread(messageBroker, "MessageBroker-Thread").start();

            while (isRunning) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Новое подключение: " + clientSocket.getInetAddress());
                
                // Создаем обработчик клиента
                ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                clientThreadPool.execute(clientHandler);
            }
        } catch (IOException e) {
            System.err.println("Ошибка сервера: " + e.getMessage());
        } finally {
            shutdown();
        }
    }

    // Регистрация клиента после успешной аутентификации
    public void registerClient(String username, ClientHandler handler) {
        connectedClients.put(username, handler);
        broadcastSystemMessage(username + " присоединился к чату.");
        System.out.println("Зарегистрирован пользователь: " + username);
        System.out.println("Активных пользователей: " + connectedClients.size());
    }

    // Удаление клиента
    public void removeClient(String username) {
        if (connectedClients.remove(username) != null) {
            broadcastSystemMessage(username + " покинул чат.");
            System.out.println("Пользователь отключен: " + username);
        }
    }

    // Отправка сообщения всем клиентам
    public void broadcastMessage(ChatMessage message) {
        for (ClientHandler client : connectedClients.values()) {
            client.sendMessage(message);
        }
    }

    // Отправка системного сообщения
    public void broadcastSystemMessage(String text) {
        ChatMessage sysMsg = new ChatMessage(
            ChatMessage.MessageType.SYSTEM_MESSAGE, 
            "Система", 
            text
        );
        broadcastMessage(sysMsg);
    }

    // Получение списка активных пользователей
    public String[] getActiveUsers() {
        return connectedClients.keySet().toArray(new String[0]);
    }

    // Для MessageBroker
    public MessageBroker getMessageBroker() {
        return messageBroker;
    }

    // Graceful shutdown
    public synchronized void shutdown() {
        if (!isRunning) return;
        isRunning = false;
        
        System.out.println("Завершение работы сервера...");
        
        // Отключаем всех клиентов
        for (ClientHandler client : connectedClients.values()) {
            client.disconnect();
        }
        
        clientThreadPool.shutdown();
        try {
            if (!clientThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                clientThreadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            clientThreadPool.shutdownNow();
        }
        
        messageBroker.shutdown(); // Останавливаем брокер
        
        System.out.println("Сервер остановлен.");
    }

    public static void main(String[] args) {
        int port = 12345;
        int maxClients = 50;
        
        ChatServer server = new ChatServer(port, maxClients);
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));
        server.start();
    }
}