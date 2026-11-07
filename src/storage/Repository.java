package storage;

import app.AppLog;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Generic file-backed table. All records live in memory; every change is written to
 * disk before the call returns, and rolled back in memory if the write fails.
 *
 * File layout: the first line is a header "#v1|nextId=N|column,names", followed by one
 * record per line. The stored nextId counter means an id is never reused, even after the
 * record holding the highest id has been deleted.
 *
 * Subclasses only describe how one record maps to and from its fields.
 */
public abstract class Repository<T> {

    private final FileStore store;
    private final Path file;
    private final String columns;
    private final Map<Integer, T> items = new LinkedHashMap<>();
    private final List<String> warnings = new ArrayList<>();
    private int nextId = 1;
    private boolean createdFresh;

    protected Repository(FileStore store, String fileName, String columns) {
        this.store = store;
        this.file = store.dir().resolve(fileName);
        this.columns = columns;
    }

    protected abstract int idOf(T item);

    protected abstract List<String> toFields(T item);

    /** Builds a record from its fields. Throw any RuntimeException if the line is invalid. */
    protected abstract T fromFields(List<String> fields);

    // ---- loading ----

    /** Reads the file into memory. Creates it on first run. Damaged lines are backed up, skipped and reported. */
    public void load() {
        items.clear();
        warnings.clear();
        createdFresh = false;
        nextId = 1;
        if (!Files.exists(file)) {
            createdFresh = true;
            persist();
            return;
        }
        int headerNext = 1;
        int bad = 0;
        for (String raw : store.readLines(file)) {
            if (raw.isBlank()) {
                continue;
            }
            if (raw.startsWith("#")) {
                headerNext = Math.max(headerNext, parseNextId(raw));
                continue;
            }
            try {
                T item = fromFields(FileStore.split(raw));
                if (items.putIfAbsent(idOf(item), item) != null) {
                    throw new IllegalArgumentException("Duplicate id.");
                }
            } catch (RuntimeException e) {
                bad++;
                AppLog.warn("Skipped an unreadable line in " + file.getFileName() + " (" + e.getClass().getSimpleName() + ")");
            }
        }
        int maxId = items.keySet().stream().max(Integer::compare).orElse(0);
        nextId = Math.max(headerNext, maxId + 1);
        if (bad > 0) {
            Path copy = store.backupCorrupt(file);
            warnings.add(bad + " unreadable line(s) in " + file.getFileName() + " were skipped. The original file was kept as "
                    + copy.getFileName() + " in the backup folder.");
            persist();
        }
    }

    private static int parseNextId(String header) {
        for (String part : header.split("\\|")) {
            if (part.startsWith("nextId=")) {
                try {
                    return Math.max(1, Integer.parseInt(part.substring(7)));
                } catch (NumberFormatException e) {
                    return 1;
                }
            }
        }
        return 1;
    }

    public boolean wasCreatedFresh() {
        return createdFresh;
    }

    /** Problems found while loading, ready to show to the user. */
    public List<String> warnings() {
        return Collections.unmodifiableList(warnings);
    }

    // ---- queries ----

    public Optional<T> find(int id) {
        return Optional.ofNullable(items.get(id));
    }

    public List<T> all() {
        return List.copyOf(items.values());
    }

    public List<T> filter(Predicate<T> test) {
        return items.values().stream().filter(test).collect(Collectors.toList());
    }

    public boolean exists(Predicate<T> test) {
        return items.values().stream().anyMatch(test);
    }

    // ---- changes ----

    /** Creates a record with the next free id and saves it. */
    public T add(IntFunction<T> factory) {
        int id = nextId;
        T item = factory.apply(id);
        items.put(id, item);
        nextId = id + 1;
        try {
            persist();
        } catch (RuntimeException e) {
            items.remove(id);
            nextId = id;
            throw e;
        }
        return item;
    }

    public void update(T item) {
        int id = idOf(item);
        T old = items.get(id);
        if (old == null) {
            throw new IllegalStateException("Cannot update a record that does not exist.");
        }
        items.put(id, item);
        try {
            persist();
        } catch (RuntimeException e) {
            items.put(id, old);
            throw e;
        }
    }

    public void remove(int id) {
        T old = items.remove(id);
        if (old == null) {
            return;
        }
        try {
            persist();
        } catch (RuntimeException e) {
            items.put(id, old);
            throw e;
        }
    }

    private void persist() {
        List<String> lines = new ArrayList<>(items.size() + 1);
        lines.add("#v1|nextId=" + nextId + "|" + columns);
        for (T item : items.values()) {
            lines.add(FileStore.join(toFields(item)));
        }
        store.writeAtomic(file, lines);
    }

    // ---- helpers for subclasses ----

    protected static void expect(List<String> fields, int count) {
        if (fields.size() != count) {
            throw new IllegalArgumentException("Wrong number of fields.");
        }
    }

    protected static boolean parseBool(String s) {
        if (s.equals("true")) {
            return true;
        }
        if (s.equals("false")) {
            return false;
        }
        throw new IllegalArgumentException("Invalid flag.");
    }
}
