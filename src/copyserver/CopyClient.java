package copyserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.DataInputStream;
import java.io.EOFException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/** Downloads one relative path from a {@link CopyServer}. */
public final class CopyClient {
    private static final int BUFFER_SIZE = 8192;

    private CopyClient() {
    }

    static long copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long total = 0;
        for (int read = input.read(buffer); read >= 0; read = input.read(buffer)) {
            if (read == 0) {
                continue;
            }
            output.write(buffer, 0, read);
            total += read;
        }
        return total;
    }

    static long copyExactly(InputStream input, OutputStream output, long expected) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long remaining = expected;
        while (remaining > 0) {
            int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read < 0) {
                throw new EOFException("Server closed before sending the declared file length");
            }
            if (read == 0) {
                continue;
            }
            output.write(buffer, 0, read);
            remaining -= read;
        }
        return expected;
    }

    public static long download(String host, int port, String remotePath, Path destination)
            throws IOException {
        if (remotePath == null || remotePath.trim().isEmpty()) {
            throw new IllegalArgumentException("A remote relative path is required");
        }
        Path absoluteDestination = destination.toAbsolutePath().normalize();
        Path parent = absoluteDestination.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IOException("Destination parent directory does not exist");
        }
        Path temporary = Files.createTempFile(parent, ".jcopy-", ".part");

        try {
            long copied;
            try (Socket socket = new Socket(host, port);
                 PrintWriter request = new PrintWriter(new OutputStreamWriter(
                         socket.getOutputStream(), StandardCharsets.UTF_8), true);
                 DataInputStream input = new DataInputStream(socket.getInputStream());
                 OutputStream output = Files.newOutputStream(temporary)) {
                request.println(remotePath.trim());
                long expected = input.readLong();
                if (expected < 0) {
                    throw new IOException("Server rejected request: " + input.readUTF());
                }
                copied = copyExactly(input, output, expected);
            }
            Files.move(temporary, absoluteDestination, StandardCopyOption.REPLACE_EXISTING);
            return copied;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static void main(String[] args) throws IOException {
        if (args == null || args.length != 4) {
            throw new IllegalArgumentException(
                    "Usage: CopyClient <host> <port> <remote-relative-path> <destination>");
        }
        long copied = download(args[0], Integer.parseInt(args[1]), args[2], Paths.get(args[3]));
        System.out.println("Copied " + copied + " bytes");
    }
}
