# JCopy client/server

Two small Java applications for copying one file from a constrained server.
The server exposes only readable regular files beneath an explicitly configured
root; absolute paths, traversal, directories, missing files, and symlink escapes
are rejected.

Build and test with:

```bash
mvn -B -ntp clean verify
```

The build fails when tests are absent and enforces at least 70% line coverage.

Run the server and client with the compiled classes on the classpath:

```text
CopyServer <port> <server-root>
CopyClient <host> <port> <remote-relative-path> <destination>
```
