import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    private static String HOST = "localhost"; // Default
    private static int PORT = 8080; // Default

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

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

    // Método startConnection
    public void startConnection(String host, int port) throws IOException {
        socket = new Socket(host, port);
        in  = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
        System.out.println("Conectado al servidor " + host + ":" + port);
    }

    private void closeQuietly() {
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { if (out != null) out.close(); } catch (IOException ignored) {}
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    public static void main(String[] args) {
        final String providedName = (args.length >= 1) ? args[0] : null;
        final String initialMsg   = (args.length >= 2) ? args[1] : null;

        boolean[] isServerOn = {true};  //mutable

        Scanner sc = new Scanner(System.in);
        Client client = new Client();

        String host = HOST;
        int port = PORT;

        // CUSTOM HOST & PORT Definition by Terminal
        if (args.length == 3) {
            host = args[1];
            port = Integer.parseInt(args[2]);
        }

        try {
            client.startConnection(host, port);
            clear();
            System.out.println("¡Te haz conectado al servidor exitosamente!");

            // Imprimir mensajes del servidor
            Thread reader = new Thread(() -> {
                try {
                    while (true) {
                        String msg = client.in.readUTF();
                        System.out.println(msg);
                    }
                } catch (IOException e) {
                    isServerOn[0] = false;  // Terminamos la conexión cuando el servidor cierra
                    System.exit(0);  // Matar el proceso del cliente
                }
            });
            reader.setDaemon(true);
            reader.start();

            // Enviar nombre si está disponible
            if (providedName != null && !providedName.isBlank()) {
                client.out.writeUTF(providedName);
                client.out.flush();
                clear();
            }

            // Mantener la conexión abierta mientras el servidor esté disponible
            while (isServerOn[0]) {
                String line = sc.nextLine();
                client.out.writeUTF(line);
                client.out.flush();
                if (line.equalsIgnoreCase("/quit")) {
                    break;
                }
            }

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        } finally {
            client.closeQuietly();
        }
    }
}
