import java.io.*;
import java.net.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;

public class SimpleChatServer {
    private static final int PORT = 12345;
    private static final ConcurrentHashMap<String, PrintWriter> clients = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Integer> userMessages = new ConcurrentHashMap<>();
    private static int totalMessages = 0;

    public static void main(String[] args) throws IOException {
        System.out.println("=== CHAT SERVER ===");
        System.out.println("Starting on port: " + PORT);
        System.out.println("Connect using: telnet localhost " + PORT);
        System.out.println("Commands: /help, /stats, /users, /time");
        
        ExecutorService pool = Executors.newFixedThreadPool(50);
        
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                pool.execute(new ClientHandler(clientSocket));
            }
        }
    }
    
    static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;
        
        public ClientHandler(Socket socket) {
            this.socket = socket;
        }
        
        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                
                // Registration
                out.println("Enter your name:");
                username = in.readLine();
                
                if (username == null || username.trim().isEmpty()) {
                    out.println("Error: name cannot be empty");
                    socket.close();
                    return;
                }
                
                synchronized (clients) {
                    if (clients.containsKey(username)) {
                        out.println("Error: this name is already taken");
                        socket.close();
                        return;
                    }
                    clients.put(username, out);
                }
                
                // Welcome
                broadcastSystemMessage(username + " joined the chat!");
                out.println("Welcome, " + username + "!");
                out.println("Online now: " + String.join(", ", clients.keySet()));
                out.println("Type /help for commands");
                
                // Main loop
                String input;
                while ((input = in.readLine()) != null) {
                    if (input.trim().isEmpty()) continue;
                    
                    if (input.equalsIgnoreCase("/quit") || input.equalsIgnoreCase("/exit")) {
                        break;
                    }
                    
                    if (input.startsWith("/")) {
                        handleCommand(input);
                    } else {
                        totalMessages++;
                        userMessages.put(username, userMessages.getOrDefault(username, 0) + 1);
                        
                        String formattedMessage = String.format("[%s] %s: %s",
                            LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                            username, input);
                        
                        System.out.println(formattedMessage);
                        broadcastMessage(formattedMessage);
                    }
                }
                
            } catch (IOException e) {
                System.out.println("Error with client " + username + ": " + e.getMessage());
            } finally {
                // Client disconnect
                if (username != null) {
                    clients.remove(username);
                    broadcastSystemMessage(username + " left the chat.");
                    System.out.println("Disconnected: " + username + ". Online: " + clients.size());
                }
                try { socket.close(); } catch (IOException e) {}
            }
        }
        
        private void handleCommand(String command) {
            String[] parts = command.substring(1).split(" ", 2);
            String cmd = parts[0].toLowerCase();
            String arg = parts.length > 1 ? parts[1] : "";
            
            switch (cmd) {
                case "help":
                    out.println("=== AVAILABLE COMMANDS ===");
                    out.println("/help - show this help");
                    out.println("/stats - your statistics");
                    out.println("/users - who is online");
                    out.println("/time - current server time");
                    out.println("/me - information about you");
                    out.println("/quit - exit chat");
                    break;
                    
                case "stats":
                    int myMessages = userMessages.getOrDefault(username, 0);
                    out.println("=== YOUR STATISTICS ===");
                    out.println("Messages: " + myMessages);
                    out.println("Total in chat: " + totalMessages);
                    out.println("Users online: " + clients.size());
                    if (totalMessages > 0) {
                        out.println("Your contribution: " + 
                            String.format("%.1f", myMessages * 100.0 / totalMessages) + "%");
                    }
                    break;
                    
                case "users":
                    out.println("=== ONLINE (" + clients.size() + ") ===");
                    clients.keySet().forEach(user -> {
                        int msgs = userMessages.getOrDefault(user, 0);
                        String status = msgs > 50 ? "[ACTIVE]" : msgs > 10 ? "[REGULAR]" : "[NEW]";
                        out.println(status + " " + user + " - " + msgs + " messages");
                    });
                    break;
                    
                case "time":
                    out.println("Server time: " + 
                        LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                    break;
                    
                case "me":
                    out.println("=== YOUR INFO ===");
                    out.println("Name: " + username);
                    out.println("Messages: " + userMessages.getOrDefault(username, 0));
                    out.println("Rank: " + getUserRank());
                    break;
                    
                default:
                    out.println("Unknown command. Type /help");
            }
        }
        
        private String getUserRank() {
            int myMessages = userMessages.getOrDefault(username, 0);
            long aboveCount = userMessages.values().stream()
                .filter(count -> count > myMessages)
                .count();
            
            int total = userMessages.size();
            if (total == 0) return "Newbie";
            
            int position = (int) aboveCount + 1;
            int percentage = (int) ((double) position / total * 100);
            
            if (position == 1) return "CHAT LEADER";
            if (percentage <= 10) return "Top-10%";
            if (percentage <= 25) return "Top-25%";
            if (percentage <= 50) return "Top-50%";
            return "Newbie";
        }
        
        private void broadcastMessage(String message) {
            for (PrintWriter writer : clients.values()) {
                writer.println(message);
            }
        }
        
        private void broadcastSystemMessage(String message) {
            String sysMsg = "[SYSTEM] " + message;
            System.out.println(sysMsg);
            for (PrintWriter writer : clients.values()) {
                writer.println(sysMsg);
            }
        }
    }
}