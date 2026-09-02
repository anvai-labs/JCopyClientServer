package copyserver;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Serves readable regular files from one explicitly configured root. */
public final class CopyServer {
    private final Path root;

    public CopyServer(Path root) throws IOException {
        this.root = root.toRealPath();
        if (!Files.isDirectory(this.root)) {
            throw new IOException("Server root is not a directory: " + root);
        }
    }

    Path resolveRequestedFile(String request) throws IOException {
        if (request == null || request.trim().isEmpty()) {
            throw new IOException("A relative file path is required");
        }
        Path requested = Paths.get(request.trim());
        if (requested.isAbsolute()) {
            throw new IOException("Absolute paths are not allowed");
        }

        Path candidate = root.resolve(requested).normalize().toRealPath();
        if (!candidate.startsWith(root)) {
            throw new IOException("Requested path escapes the configured server root");
        }
        if (!Files.isRegularFile(candidate) || !Files.isReadable(candidate)) {
            throw new IOException("Requested path is not a readable regular file");
        }
        return candidate;
    }

    void serveOnce(ServerSocket listener) throws IOException {
        try (Socket client = listener.accept();
             BufferedReader request = new BufferedReader(new InputStreamReader(
                     client.getInputStream(), StandardCharsets.UTF_8));
             DataOutputStream output = new DataOutputStream(client.getOutputStream())) {
            try {
                Path file = resolveRequestedFile(request.readLine());
                output.writeLong(Files.size(file));
                Files.copy(file, output);
            } catch (IOException error) {
                output.writeLong(-1L);
                output.writeUTF(error.getMessage());
            }
            output.flush();
        }
    }

    public void serve(int port) throws IOException {
        try (ServerSocket listener = new ServerSocket(port)) {
            while (!Thread.currentThread().isInterrupted()) {
                serveOnce(listener);
            }
        }
    }

    public static void main(String[] args) throws IOException {
        if (args == null || args.length != 2) {
            throw new IllegalArgumentException("Usage: CopyServer <port> <server-root>");
        }
        new CopyServer(Paths.get(args[1])).serve(Integer.parseInt(args[0]));
    }
}
