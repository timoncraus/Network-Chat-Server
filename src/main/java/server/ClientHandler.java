package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

import common.ChatMessage;

public class ClientHandler implements Runnable {
    private static final int MAX_MESSAGE_LENGTH = 1000; // Максимальная длина сообщения
    private static final int MESSAGE_LIMIT_PER_MINUTE = 60; // Максимальное количество сообщений в минуту
    private static final ConcurrentHashMap<String, UserRateLimiter> rateLimiters = new ConcurrentHashMap<>();
    
    private Socket socket;
    private ChatServer server;
    private PrintWriter out;
    private BufferedReader in;
    private String username;
    private volatile boolean isConnected;

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
        this.isConnected = true;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Этап 1: Регистрация (запрос имени пользователя)
            out.println("Введите ваше имя:");
            username = in.readLine();
            
            if (username == null || username.trim().isEmpty()) {
                out.println("Имя не может быть пустым. Соединение закрыто.");
                disconnect();
                return;
            }
            
            // Проверка на уникальность имени (упрощенно)
            // В реальной системе нужна более сложная логика
            
            // Регистрируем клиента на сервере
            server.registerClient(username, this);
            out.println("Добро пожаловать в чат, " + username + "! Для помощи введите /help");

            // Этап 2: Основной цикл обработки сообщений
            String inputLine;
            while (isConnected && (inputLine = in.readLine()) != null) {
                if (inputLine.trim().isEmpty()) continue;
                
                // Проверка длины сообщения
                if (inputLine.length() > MAX_MESSAGE_LENGTH) {
                    out.println("Сообщение слишком длинное. Максимальная длина: " + MAX_MESSAGE_LENGTH + " символов.");
                    continue;
                }
                
                // Проверка рейт-лимита
                UserRateLimiter limiter = rateLimiters.computeIfAbsent(username,
                    k -> new UserRateLimiter(MESSAGE_LIMIT_PER_MINUTE));
                
                if (!limiter.allowRequest()) {
                    out.println("Превышен лимит сообщений в минуту (" + MESSAGE_LIMIT_PER_MINUTE + "). Попробуйте позже.");
                    continue;
                }
                
                // Создаем сообщение
                ChatMessage.MessageType type = inputLine.startsWith("/")
                    ? ChatMessage.MessageType.COMMAND
                    : ChatMessage.MessageType.USER_MESSAGE;
                
                ChatMessage message = new ChatMessage(type, username, inputLine);
                
                // Отправляем сообщение в MessageBroker
                server.getMessageBroker().processIncomingMessage(message);
            }
        } catch (IOException e) {
            Logger.error("ClientHandler", "Ошибка ввода-вывода в обработчике клиента " + username + ": " + e.getMessage(), e);
        } catch (Exception e) {
            Logger.error("ClientHandler", "Неожиданная ошибка в обработчике клиента " + username, e);
        } finally {
            disconnect();
        }
    }

    // Отправка сообщения этому клиенту
    public void sendMessage(ChatMessage message) {
        if (out != null && isConnected) {
            String formattedMessage;
            if (message.getType() == ChatMessage.MessageType.SYSTEM_MESSAGE) {
                formattedMessage = "[СИСТЕМА] " + message.getText();
            } else if (message.getType() == ChatMessage.MessageType.STATISTICS) {
                formattedMessage = "[БОТ] " + message.getText();
            } else {
                formattedMessage = String.format("[%s] %s", message.getUser(), message.getText());
            }
            out.println(formattedMessage);
        }
    }

    // Корректное отключение
    public void disconnect() {
        if (!isConnected) return;
        
        isConnected = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            Logger.error("Ошибка при закрытии сокета: " + e.getMessage());
        }
        
        // Удаляем клиента из сервера
        if (username != null) {
            server.removeClient(username);
        }
        
        Logger.info("Клиент отключен: " + username);
    }

    public String getUsername() {
        return username;
    }

    public boolean isConnected() {
        return isConnected;
    }
}