package storage;

import app.AppLog;
import app.Config;
import exception.StorageException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.CharsetDecoder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Low-level file handling shared by all repositories:
 * a single-instance lock, crash-safe writes, backups and the line format.
 *
 * Line format: fields separated by '|'. Inside a field, '|' is written as "\|",
 * a backslash as "\\", and line breaks as "\n" and "\r".
 */
public final class FileStore implements AutoCloseable {

    private static final String GENERIC_WRITE_ERROR = "Could not save the data. Nothing was changed.";
    private static final String INVALID_UTF8_LINE = "\u0000INVALID_UTF8_LINE";
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(Config.ZONE);

    private final Path dir;
    private FileChannel lockChannel;
    private FileLock lock;

    public FileStore(Path dir) {
        this.dir = dir.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.dir);
            restrict(this.dir, true);
        } catch (IOException e) {
            AppLog.error("Cannot create data folder", e);
            throw new StorageException("Could not prepare the data folder.", e);
        }
    }

    public Path dir() {
        return dir;
    }

    /**
     * Takes an exclusive lock so two copies of the program cannot write the same files.
     *
     * @return false if another copy already holds the lock
     */
    public boolean acquireLock() {
        try {
            Path lockFile = dir.resolve(".lock");
            lockChannel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            restrict(lockFile, false);
            lock = lockChannel.tryLock();
            if (lock == null) {
                lockChannel.close();
                lockChannel = null;
                return false;
            }
            return true;
        } catch (OverlappingFileLockException e) {
            return false;
        } catch (IOException e) {
            AppLog.error("Cannot create lock file", e);
            throw new StorageException("Could not prepare the data folder.", e);
        }
    }

    @Override
    public void close() {
        try {
            if (lock != null && lock.isValid()) {
                lock.release();
            }
            if (lockChannel != null && lockChannel.isOpen()) {
                lockChannel.close();
            }
        } catch (IOException e) {
            // Nothing useful to do while shutting down.
        } finally {
            lock = null;
            lockChannel = null;
        }
    }

    /** Reads a UTF-8 file as lines. Malformed lines become invalid sentinels for repository quarantine. */
    public List<String> readLines(Path file) {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try {
            return decodeLines(Files.readAllBytes(file));
        } catch (IOException e) {
            AppLog.error("Cannot read " + file.getFileName(), e);
            throw new StorageException("Could not read the data files.", e);
        }
    }

    private static List<String> decodeLines(byte[] bytes) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        int lineNumber = 0;
        for (int i = 0; i <= bytes.length; i++) {
            if (i != bytes.length && bytes[i] != '\n') continue;
            int end = i;
            if (i < bytes.length && end > start && bytes[end - 1] == '\r') end--;
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            String line;
            try {
                line = decoder.decode(ByteBuffer.wrap(bytes, start, end - start)).toString();
                if (lineNumber == 0 && line.startsWith("\uFEFF")) line = line.substring(1);
            } catch (CharacterCodingException e) {
                line = INVALID_UTF8_LINE;
            }
            lines.add(line);
            lineNumber++;
            start = i + 1;
        }
        return lines;
    }

    /**
     * Writes the whole file safely: keep the previous version as .bak, write a temporary
     * file, force it to disk, then swap it into place in one step. A crash at any point
     * leaves either the old or the new complete file, never a half-written one.
     */
    public void writeAtomic(Path file, List<String> lines) {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Path bak = file.resolveSibling(file.getFileName() + ".bak");
        try {
            if (Files.exists(file)) {
                Files.copy(file, bak, StandardCopyOption.REPLACE_EXISTING);
                restrict(bak, false);
            }
            byte[] data = (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8);
            try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buf = ByteBuffer.wrap(data);
                while (buf.hasRemaining()) {
                    ch.write(buf);
                }
                ch.force(true);
            }
            restrict(tmp, false);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            AppLog.error("Cannot write " + file.getFileName(), e);
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // best effort clean-up
            }
            throw new StorageException(GENERIC_WRITE_ERROR, e);
        }
    }

    /** Copies a damaged file into the backup folder before anything overwrites it. */
    public Path backupCorrupt(Path file) {
        try {
            Path backupDir = dir.resolve("backup");
            Files.createDirectories(backupDir);
            restrict(backupDir, true);
            Path target = backupDir.resolve(file.getFileName() + ".corrupt-" + STAMP.format(Config.clock().instant()));
            Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
            restrict(target, false);
            return target;
        } catch (IOException e) {
            AppLog.error("Cannot back up damaged file " + file.getFileName(), e);
            throw new StorageException("Could not back up a damaged data file.", e);
        }
    }

    /** Owner-only permissions where the file system supports them (Linux, macOS). */
    public static void restrict(Path path, boolean directory) {
        try {
            Files.setPosixFilePermissions(path,
                    PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
        } catch (UnsupportedOperationException | IOException e) {
            // Not a POSIX file system (for example Windows). Nothing to do.
        }
    }

    // ---- line format ----

    public static String join(List<String> fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append(escape(fields.get(i)));
        }
        return sb.toString();
    }

    public static String escape(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 4);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '|' -> sb.append("\\|");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Splits one line into unescaped fields. Throws IllegalArgumentException on a malformed escape. */
    public static List<String> split(String line) {
        if (INVALID_UTF8_LINE.equals(line)) {
            throw new IllegalArgumentException("Malformed UTF-8 line.");
        }
        List<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\') {
                if (i + 1 >= line.length()) {
                    throw new IllegalArgumentException("Dangling escape.");
                }
                char n = line.charAt(++i);
                switch (n) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case '\\', '|' -> sb.append(n);
                    default -> throw new IllegalArgumentException("Unknown escape.");
                }
            } else if (c == '|') {
                fields.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        fields.add(sb.toString());
        return fields;
    }
}
