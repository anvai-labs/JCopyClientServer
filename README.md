# JCopy client/server

Two small Java 8-compatible applications for copying one file from a constrained server.
The server exposes only readable regular files beneath an explicitly configured
root; absolute paths, traversal, directories, missing files, and symlink escapes
are rejected.

This is a minimal protocol example, not an internet-facing file-transfer service. The
wire protocol has no authentication, authorization identity, encryption, rate limiting,
or concurrent request handling. Use it only on a trusted private network; add TLS and
mutual authentication before adapting it for production use.

Build and test with:

```bash
mvn -B -ntp clean verify
```

CI builds and tests on Java 8, 17, and 21. The build fails when tests are absent and
enforces at least 70% line coverage.

Run the server and client with the compiled classes on the classpath:

```bash
java -cp target/classes copyserver.CopyServer <port> <server-root>
java -cp target/classes copyserver.CopyClient \
  <host> <port> <remote-relative-path> <destination>
```

The destination's parent directory must already exist. A successful download is first
written to a temporary file and then atomically moved over the destination.
