import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;

public class Server {

    private static final int PORT = 8080;

    private static final ConcurrentMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Esta funcion es para limpiar la terminal en cualquier OS
    public static void clear() {
        String sistemaOperativo = System.getProperty("os.name").toLowerCase();
        
        try {
            if (sistemaOperativo.contains("nix") || sistemaOperativo.contains("nux") || sistemaOperativo.contains("mac")) {
                // Unix/Mac
                ProcessBuilder process = new ProcessBuilder("clear");
                process.inheritIO();
                process.start();
            } 
            else if (sistemaOperativo.contains("win")) {
                // Windows
                ProcessBuilder process = new ProcessBuilder("cmd", "/c", "cls");
                process.inheritIO();
                process.start();
            }
        } catch (IOException e) {
            System.out.println("Error al limpiar la terminal: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        System.out.println("Servidor de chat iniciado en puerto " + PORT);

        // Hilo para leer mensajes del operador del servidor
        Thread consoleBroadcaster = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    
                    // NUEVA FUNCIONALIDAD: Comando /kick para eliminar usuarios
                    if (line.startsWith("/kick ")) {
                        String userToKick = line.substring(6).trim();
                        if (!userToKick.isEmpty()) {
                            kickUser(userToKick);
                        } else {
                            System.out.println("Uso: /kick NOMBRE_USUARIO");
                        }
                        continue;
                    }
                    
                    sendGlobalMessage(null, line);
                    logMessage("Server", line);
                }
            } catch (IOException ignored) {}
        }, "ServerConsoleBroadcaster");
        consoleBroadcaster.setDaemon(true);
        consoleBroadcaster.start();

        clear();

        System.out.println("¡Servidor creado exitosamente!");
        System.out.println("[NUEVO] Comandos disponibles: ");
        System.out.println("/kick USUARIO\tEliminar un usuario del chat");
        
        // Aceptar clientes
        try (ServerSocket server = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = server.accept();
                ClientHandler handler = new ClientHandler(socket);
                new Thread(handler, "ClientHandler-" + socket.getPort()).start();
            }
        } catch (IOException e) {
            System.err.println("Error en servidor: " + e.getMessage());
        }
    }

    // Historial de mensajes
    static void logMessage(String user, String message) {
        String ts = LocalDateTime.now().format(TS);
        String who = (user == null ? "[Server]" : user);
        System.out.println(ts + " [" + who + "]: " + message);
    }

    // Enviar mensaje global
    static void sendGlobalMessage(String fromUser, String message) {
        String payload = (fromUser == null)
                ? "[Server]: " + message
                : "[" + fromUser + "]: " + message;

        for (ClientHandler ch : clients.values()) {
            ch.send(payload);
        }
    }

    // Enviar mensaje privado
    static boolean sendPrivateMessage(String fromUser, String toUser, String message) {
        ClientHandler target = clients.get(toUser);
        if (target == null) return false;
        target.send("[Private][" + fromUser + "]: " + message);
        return true;
    }

    // Cambiar nombre de usuario
    static synchronized boolean changeUserName(String oldName, String newName, ClientHandler handler) {
        if (newName == null || newName.isBlank() || clients.containsKey(newName)) return false;
        clients.remove(oldName);
        clients.put(newName, handler);
        return true;
    }

    // Registrar un nuevo usuario
    static synchronized boolean registerNewUser(String user, ClientHandler handler) {
        if (user == null || user.isBlank() || clients.containsKey(user)) return false;
        clients.put(user, handler);
        return true;
    }

    // Eliminar un usuario
    static void removeUser(String username) {
        if (username != null) {
            clients.remove(username);
            sendGlobalMessage(null, username + " salió del chat.");
            logMessage("Server", username + " salió del chat.");
        }
    }

    // NUEVA FUNCIONALIDAD: Eliminar usuario desde el servidor (kick)
    static void kickUser(String username) {
        ClientHandler target = clients.get(username);
        if (target != null) {
            // Notificar al usuario que será eliminado
            target.send("[Server]: Has sido eliminado del chat por el administrador.");
            
            // Forzar desconexión
            target.forceDisconnect();
            
            // Remover del mapa de clientes
            clients.remove(username);
            
            // Notificar a todos los usuarios
            sendGlobalMessage(null, username + " ha sido eliminado del chat por el administrador.");
            logMessage("Server", "Usuario " + username + " eliminado por el administrador.");
        } else {
            System.out.println("Usuario '" + username + "' no encontrado.");
            logMessage("Server", "Intento de eliminar usuario inexistente: " + username);
        }
    }

    
    static class ClientHandler implements Runnable {
        private final Socket socket;
        private DataInputStream in;
        private DataOutputStream out;
        private volatile String username;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (Socket s = socket) {
                in  = new DataInputStream(s.getInputStream());
                out = new DataOutputStream(s.getOutputStream());

                // Solicitar nombre de usuario
                while (true) {
                    send("Bienvenido. Ingresa tu nombre de usuario:");
                    String requested = in.readUTF().trim();

                    if (Server.registerNewUser(requested, this)) {
                        username = requested;
                        send("...");

                        // Notificar al resto de los clientes que un nuevo usuario se unió
                        Server.sendGlobalMessage(null, username + " se unió al chat.");
                        logMessage("Server", username + " se unió al chat.");
                        break;
                    } else {
                        send("Nombre inválido o en uso. Intenta otro:");
                    }
                }

                // Ahora que el usuario se unió, el cliente puede comenzar a interactuar
                String line;
                while ((line = in.readUTF()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    logMessage(username, line);

                    if (line.equalsIgnoreCase("/help")) {
                        send("""
    Comandos:
    /rename NUEVO_NOMBRE      -> Cambia tu nombre
    /msg USUARIO MENSAJE      -> Envia mensaje privado
    /all MENSAJE              -> Envia mensaje global
    /quit                     -> Salir
    Por defecto, los mensajes se envían globalmente.
    """);
                        continue;
                    }

                    if (line.equalsIgnoreCase("/quit")) {
                        send("Saliendo del chat. ¡Hasta luego!");
                        break;
                    }

                    if (line.startsWith("/rename ")) {
                        String newName = line.substring(8).trim();
                        if (Server.changeUserName(username, newName, this)) {
                            String old = username;
                            username = newName;
                            send("Tu nombre ahora es: " + username);
                            Server.sendGlobalMessage(null, old + " cambió su nombre a " + username + ".");
                            logMessage("Server", old + " cambió su nombre a " + username + ".");
                        } else {
                            send("No se pudo cambiar el nombre (vacío o ya en uso).");
                        }
                        continue;
                    }

                    if (line.startsWith("/msg ")) {
                        String[] parts = line.split("\\s+", 3);
                        if (parts.length < 3) {
                            send("Uso: /msg _USUARIO_ _MENSAJE_");
                        } else {
                            String toUser = parts[1];
                            String msg = parts[2];
                            boolean ok = Server.sendPrivateMessage(username, toUser, msg);
                            if (!ok) {
                                send("Usuario '" + toUser + "' no encontrado.");
                            }
                        }
                        continue;
                    }

                    if (line.startsWith("/all ")) {
                        String msg = line.substring(5).trim();
                        if (!msg.isEmpty()) {
                            Server.sendGlobalMessage(username, msg);
                        }
                        continue;
                    }

                    // Por defecto: mensaje global
                    Server.sendGlobalMessage(username, line);
                }

            } catch (EOFException eof) {
                // cliente cerró
            } catch (IOException e) {
                // System.err.println("Error con cliente " + username + ": " + e.getMessage());
            } finally {
                Server.removeUser(username);
                closeQuietly(in);
                closeQuietly(out);
            }
        }

        void send(String msg) {
            try {
                out.writeUTF(msg);
                out.flush();
            } catch (IOException ignored) {}
        }

        // Forzar desconexión del user
        void forceDisconnect() {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException ignored) {}
        }

        private static void closeQuietly(Closeable c) {
            if (c != null) try { c.close(); } catch (IOException ignored) {}
        }
    }
}