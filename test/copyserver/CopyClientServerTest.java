package copyserver;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CopyClientServerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void copiesArbitraryBytes() throws IOException {
        byte[] payload = new byte[] {0, 1, 2, 10, 13, -1, 42};
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        long copied = CopyClient.copy(new ByteArrayInputStream(payload), output);

        assertEquals(payload.length, copied);
        assertArrayEquals(payload, output.toByteArray());

        ByteArrayOutputStream exactOutput = new ByteArrayOutputStream();
        assertEquals(payload.length, CopyClient.copyExactly(
                new ByteArrayInputStream(payload), exactOutput, payload.length));
        assertArrayEquals(payload, exactOutput.toByteArray());
        assertThrows(EOFException.class, () -> CopyClient.copyExactly(
                new ByteArrayInputStream(new byte[] {1}), new ByteArrayOutputStream(), 2));
    }

    @Test
    void rejectsAbsoluteTraversalDirectoryAndMissingRequests() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("root"));
        Files.createDirectory(root.resolve("directory"));
        Path sibling = temporaryDirectory.resolve("secret.txt");
        Files.write(sibling, "secret".getBytes(StandardCharsets.UTF_8));
        CopyServer server = new CopyServer(root);

        assertThrows(IOException.class, () -> server.resolveRequestedFile(null));
        assertThrows(IOException.class, () -> server.resolveRequestedFile(" "));
        assertThrows(IOException.class, () -> server.resolveRequestedFile(sibling.toString()));
        assertThrows(IOException.class, () -> server.resolveRequestedFile("../secret.txt"));
        assertThrows(IOException.class, () -> server.resolveRequestedFile("directory"));
        assertThrows(IOException.class, () -> server.resolveRequestedFile("missing.txt"));
    }

    @Test
    void downloadsOnlyAFileInsideTheConfiguredRoot() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("root"));
        Path nested = Files.createDirectories(root.resolve("nested"));
        byte[] payload = "exact payload\nwith a second line\n".getBytes(StandardCharsets.UTF_8);
        Path source = nested.resolve("payload.bin");
        Files.write(source, payload);
        CopyServer server = new CopyServer(root);
        assertEquals(source.toRealPath(), server.resolveRequestedFile("nested/payload.bin"));

        AtomicReference<Throwable> serverFailure = new AtomicReference<Throwable>();
        Path destination = temporaryDirectory.resolve("download.bin");
        try (ServerSocket listener = new ServerSocket(0)) {
            Thread serverThread = new Thread(() -> {
                try {
                    server.serveOnce(listener);
                } catch (Throwable error) {
                    serverFailure.set(error);
                }
            });
            serverThread.start();

            long copied = CopyClient.download(
                    "127.0.0.1", listener.getLocalPort(), "nested/payload.bin", destination);
            serverThread.join(5000);

            assertFalse(serverThread.isAlive(), "server did not finish the single request");
            assertEquals(null, serverFailure.get());
            assertEquals(payload.length, copied);
            assertArrayEquals(payload, Files.readAllBytes(destination));
        }
    }

    @Test
    void validatesClientInputsBeforeConnecting() throws IOException {
        Path destination = temporaryDirectory.resolve("download.bin");
        assertThrows(IllegalArgumentException.class,
                () -> CopyClient.download("127.0.0.1", 1, " ", destination));
        Path missingParent = temporaryDirectory.resolve("missing").resolve("download.bin");
        assertThrows(IOException.class,
                () -> CopyClient.download("127.0.0.1", 1, "file.txt", missingParent));
        assertFalse(Files.exists(destination));
        assertTrue(Files.isDirectory(temporaryDirectory));
    }

    @Test
    void reportsRejectedServerPathWithoutCreatingDestination() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("root"));
        Path secret = temporaryDirectory.resolve("secret.txt");
        Files.write(secret, "secret".getBytes(StandardCharsets.UTF_8));
        CopyServer server = new CopyServer(root);
        AtomicReference<Throwable> serverFailure = new AtomicReference<Throwable>();
        Path destination = temporaryDirectory.resolve("rejected.bin");

        try (ServerSocket listener = new ServerSocket(0)) {
            Thread serverThread = new Thread(() -> {
                try {
                    server.serveOnce(listener);
                } catch (Throwable error) {
                    serverFailure.set(error);
                }
            });
            serverThread.start();

            IOException error = assertThrows(IOException.class, () -> CopyClient.download(
                    "127.0.0.1", listener.getLocalPort(), "../secret.txt", destination));
            serverThread.join(5000);

            assertTrue(error.getMessage().contains("rejected"));
            assertEquals(null, serverFailure.get());
            assertFalse(Files.exists(destination));
        }
    }

    @Test
    void rejectsNonDirectoryServerRoot() throws IOException {
        Path file = temporaryDirectory.resolve("file.txt");
        Files.write(file, new byte[] {1});
        assertThrows(IOException.class, () -> new CopyServer(file));
        assertThrows(IOException.class, () -> new CopyServer(Paths.get("missing-root")));
    }
}
