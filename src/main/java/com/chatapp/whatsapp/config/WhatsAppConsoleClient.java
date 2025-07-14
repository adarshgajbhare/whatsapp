package com.chatapp.whatsapp.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;

/**
 * Console-based WhatsApp client for testing WebSocket messaging
 *
 * Usage:
 * 1. Run the Spring Boot server
 * 2. Run this console client
 * 3. Use commands to interact with the messaging system
 */
public class WhatsAppConsoleClient {

    private static final String SERVER_URL = "ws://localhost:8080/ws";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private WebSocketClient client;
    private Scanner scanner;
    private boolean connected = false;
    private String currentUsername;
    private Long currentUserId;
    private Long currentConversationId;

    static {
        objectMapper.registerModule(new JavaTimeModule());
    }

    public static void main(String[] args) {
        WhatsAppConsoleClient consoleClient = new WhatsAppConsoleClient();
        consoleClient.start();
    }

    public void start() {
        scanner = new Scanner(System.in);

        System.out.println("=================================");
        System.out.println("WhatsApp Console Client");
        System.out.println("=================================");

        connectToServer();

        if (connected) {
            showHelp();
            processCommands();
        }

        cleanup();
    }

    private void connectToServer() {
        try {
            URI serverURI = new URI(SERVER_URL);
            CountDownLatch connectionLatch = new CountDownLatch(1);

            client = new WebSocketClient(serverURI) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    System.out.println("✅ Connected to WhatsApp server!");
                    connected = true;
                    connectionLatch.countDown();
                }

                @Override
                public void onMessage(String message) {
                    handleIncomingMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("❌ Disconnected from server: " + reason);
                    connected = false;
                }

                @Override
                public void onError(Exception ex) {
                    System.err.println("❌ WebSocket error: " + ex.getMessage());
                    connected = false;
                }
            };

            client.connect();

            // Wait for connection with timeout
            boolean connected = connectionLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!connected) {
                System.err.println("❌ Failed to connect to server within 5 seconds");
                System.err.println("Make sure the Spring Boot server is running on localhost:8080");
            }

        } catch (Exception e) {
            System.err.println("❌ Error connecting to server: " + e.getMessage());
        }
    }

    private void handleIncomingMessage(String message) {
        try {
            // Try to parse as JSON to see if it's a structured message
            if (message.startsWith("{")) {
                Map<String, Object> messageData = objectMapper.readValue(message, Map.class);

                String messageType = (String) messageData.get("messageType");
                String content = (String) messageData.get("content");
                String senderUsername = (String) messageData.get("senderUsername");
                String sentAt = (String) messageData.get("sentAt");

                switch (messageType != null ? messageType : "TEXT") {
                    case "TEXT":
                        System.out.println(String.format("\n💬 [%s] %s: %s",
                                sentAt != null ? sentAt.substring(11, 19) : "now",
                                senderUsername, content));
                        break;
                    case "JOIN":
                        System.out.println(String.format("\n👋 %s", content));
                        break;
                    case "TYPING":
                        System.out.println(String.format("\n⌨️  %s %s", senderUsername, content));
                        break;
                    case "ERROR":
                        System.out.println(String.format("\n❌ Error: %s", content));
                        break;
                    default:
                        System.out.println(String.format("\n📝 %s: %s", senderUsername, content));
                }
            } else {
                // Plain text message
                System.out.println("\n📨 " + message);
            }

            System.out.print("\nwhatsapp> ");

        } catch (Exception e) {
            System.out.println("\n📨 " + message);
            System.out.print("\nwhatsapp> ");
        }
    }

    private void processCommands() {
        String input;
        System.out.print("whatsapp> ");

        while ((input = scanner.nextLine()) != null) {
            if (input.trim().isEmpty()) {
                System.out.print("whatsapp> ");
                continue;
            }

            String[] parts = input.trim().split("\\s+", 3);
            String command = parts[0].toLowerCase();

            try {
                switch (command) {
                    case "/help":
                        showHelp();
                        break;
                    case "/login":
                        handleLogin(parts);
                        break;
                    case "/search":
                        handleSearch(parts);
                        break;
                    case "/chat":
                        handleStartChat(parts);
                        break;
                    case "/join":
                        handleJoinConversation(parts);
                        break;
                    case "/send":
                        handleSendMessage(parts);
                        break;
                    case "/typing":
                        handleTyping();
                        break;
                    case "/status":
                        showStatus();
                        break;
                    case "/quit":
                    case "/exit":
                        return;
                    default:
                        // If not a command, treat as message
                        if (currentConversationId != null) {
                            sendMessage(input);
                        } else {
                            System.out.println("❌ Unknown command. Type /help for available commands.");
                        }
                }
            } catch (Exception e) {
                System.out.println("❌ Error: " + e.getMessage());
            }

            System.out.print("whatsapp> ");
        }
    }

    private void handleLogin(String[] parts) {
        if (parts.length < 3) {
            System.out.println("Usage: /login <username> <password>");
            return;
        }

        // For demo purposes, we'll simulate login
        currentUsername = parts[1];
        currentUserId = (long) currentUsername.hashCode(); // Simple ID generation

        System.out.println("✅ Logged in as: " + currentUsername);
        System.out.println("💡 You can now search for users and start chatting!");
    }

    private void handleSearch(String[] parts) {
        if (parts.length < 2) {
            System.out.println("Usage: /search <username>");
            return;
        }

        String searchUsername = parts[1];
        System.out.println("🔍 Searching for user: " + searchUsername);

        // In a real implementation, this would make an HTTP request to the search API
        System.out.println("👤 Found user: " + searchUsername + " (ID: " + searchUsername.hashCode() + ")");
        System.out.println("💡 Use '/chat " + searchUsername + "' to start a conversation");
    }

    private void handleStartChat(String[] parts) {
        if (parts.length < 2) {
            System.out.println("Usage: /chat <username>");
            return;
        }

        if (currentUsername == null) {
            System.out.println("❌ Please login first using /login <username> <password>");
            return;
        }

        String targetUsername = parts[1];
        // Generate a conversation ID (in real app, this would come from the server)
        currentConversationId = (long) (currentUsername + "_" + targetUsername).hashCode();

        System.out.println("💬 Starting chat with: " + targetUsername);
        System.out.println("📝 Conversation ID: " + currentConversationId);
        System.out.println("💡 You can now type messages directly or use /send <message>");

        // Join the conversation
        joinConversation(currentConversationId, targetUsername);
    }

    private void handleJoinConversation(String[] parts) {
        if (parts.length < 2) {
            System.out.println("Usage: /join <conversationId>");
            return;
        }

        try {
            currentConversationId = Long.parseLong(parts[1]);
            String targetUser = parts.length > 2 ? parts[2] : "Unknown";
            joinConversation(currentConversationId, targetUser);
        } catch (NumberFormatException e) {
            System.out.println("❌ Invalid conversation ID");
        }
    }

    private void joinConversation(Long conversationId, String targetUser) {
        try {
            Map<String, Object> joinMessage = new HashMap<>();
            joinMessage.put("conversationId", conversationId);
            joinMessage.put("senderId", currentUserId);
            joinMessage.put("senderUsername", currentUsername);
            joinMessage.put("content", "Joined conversation with " + targetUser);
            joinMessage.put("messageType", "JOIN");
            joinMessage.put("sentAt", LocalDateTime.now().toString());

            // Send join message via STOMP
            sendStompMessage("/app/chat.joinConversation", joinMessage);

            // Subscribe to conversation updates
            subscribeToConversation(conversationId);

        } catch (Exception e) {
            System.out.println("❌ Error joining conversation: " + e.getMessage());
        }
    }

    private void handleSendMessage(String[] parts) {
        if (parts.length < 2) {
            System.out.println("Usage: /send <message>");
            return;
        }

        String messageText = String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length));
        sendMessage(messageText);
    }

    private void sendMessage(String content) {
        if (currentConversationId == null) {
            System.out.println("❌ No active conversation. Use /chat <username> first.");
            return;
        }

        if (currentUsername == null) {
            System.out.println("❌ Please login first using /login <username> <password>");
            return;
        }

        try {
            Map<String, Object> message = new HashMap<>();
            message.put("conversationId", currentConversationId);
            message.put("senderId", currentUserId);
            message.put("senderUsername", currentUsername);
            message.put("content", content);
            message.put("messageType", "TEXT");
            message.put("sentAt", LocalDateTime.now().toString());

            sendStompMessage("/app/chat.sendMessage", message);

        } catch (Exception e) {
            System.out.println("❌ Error sending message: " + e.getMessage());
        }
    }

    private void handleTyping() {
        if (currentConversationId == null) {
            System.out.println("❌ No active conversation.");
            return;
        }

        try {
            Map<String, Object> typingMessage = new HashMap<>();
            typingMessage.put("conversationId", currentConversationId);
            typingMessage.put("senderId", currentUserId);
            typingMessage.put("senderUsername", currentUsername);
            typingMessage.put("messageType", "TYPING");
            typingMessage.put("sentAt", LocalDateTime.now().toString());

            sendStompMessage("/app/chat.typing", typingMessage);

        } catch (Exception e) {
            System.out.println("❌ Error sending typing indicator: " + e.getMessage());
        }
    }

    private void sendStompMessage(String destination, Map<String, Object> message) {
        try {
            // Simple STOMP frame format
            StringBuilder stompFrame = new StringBuilder();
            stompFrame.append("SEND\n");
            stompFrame.append("destination:").append(destination).append("\n");
            stompFrame.append("content-type:application/json\n");
            stompFrame.append("\n");
            stompFrame.append(objectMapper.writeValueAsString(message));
            stompFrame.append("\0");

            client.send(stompFrame.toString());

        } catch (Exception e) {
            System.out.println("❌ Error sending STOMP message: " + e.getMessage());
        }
    }

    private void subscribeToConversation(Long conversationId) {
        try {
            // STOMP SUBSCRIBE frame
            StringBuilder subscribeFrame = new StringBuilder();
            subscribeFrame.append("SUBSCRIBE\n");
            subscribeFrame.append("id:sub-").append(conversationId).append("\n");
            subscribeFrame.append("destination:/topic/conversation/").append(conversationId).append("\n");
            subscribeFrame.append("\n");
            subscribeFrame.append("\0");

            client.send(subscribeFrame.toString());

            System.out.println("🔔 Subscribed to conversation updates");

        } catch (Exception e) {
            System.out.println("❌ Error subscribing to conversation: " + e.getMessage());
        }
    }

    private void showStatus() {
        System.out.println("\n📊 Current Status:");
        System.out.println("Connection: " + (connected ? "✅ Connected" : "❌ Disconnected"));
        System.out.println("User: " + (currentUsername != null ? currentUsername + " (ID: " + currentUserId + ")" : "Not logged in"));
        System.out.println("Active Conversation: " + (currentConversationId != null ? currentConversationId : "None"));
    }

    private void showHelp() {
        System.out.println("\n🔧 Available Commands:");
        System.out.println("/login <username> <password>  - Login to the system");
        System.out.println("/search <username>            - Search for a user");
        System.out.println("/chat <username>              - Start a chat with user");
        System.out.println("/join <conversationId>        - Join specific conversation");
        System.out.println("/send <message>               - Send a message");
        System.out.println("/typing                       - Send typing indicator");
        System.out.println("/status                       - Show current status");
        System.out.println("/help                         - Show this help");
        System.out.println("/quit or /exit                - Exit the application");
        System.out.println("\n💡 Tip: Once in a conversation, you can type messages directly without /send");
        System.out.println();
    }

    private void cleanup() {
        if (client != null && client.isOpen()) {
            client.close();
        }
        if (scanner != null) {
            scanner.close();
        }
        System.out.println("👋 Goodbye!");
    }
}