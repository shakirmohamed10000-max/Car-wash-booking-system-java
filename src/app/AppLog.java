package app;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * File-only logging. Nothing is ever echoed to the terminal, and anything that
 * looks like a phone number is masked before it reaches the file.
 */
public final class AppLog {

    private static final Logger LOG = Logger.getLogger("carwash");
    private static final Pattern PHONE = Pattern.compile("(?:\\+?254|0)[17]\\d{8}");

    static {
        LOG.setUseParentHandlers(false);
    }

    private AppLog() {
    }

    public static void init(Path dir) {
        try {
            Files.createDirectories(dir);
            FileHandler handler = new FileHandler(dir.resolve("app.%g.log").toString(), 512_000, 3, true);
            handler.setEncoding("UTF-8");
            handler.setFormatter(new Formatter() {
                @Override
                public String format(LogRecord r) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(Instant.ofEpochMilli(r.getMillis())).append(' ')
                            .append(String.format("%-7s", r.getLevel().getName())).append(' ')
                            .append(mask(formatMessage(r))).append(System.lineSeparator());
                    if (r.getThrown() != null) {
                        StringWriter sw = new StringWriter();
                        r.getThrown().printStackTrace(new PrintWriter(sw));
                        sb.append(mask(sw.toString()));
                    }
                    return sb.toString();
                }
            });
            LOG.addHandler(handler);
            LOG.setLevel(Level.INFO);
            storage.FileStore.restrict(dir, true);
        } catch (IOException | SecurityException e) {
            // Logging is best effort. The program keeps working without it.
        }
    }

    /** Replaces the middle digits of any phone-like number. */
    public static String mask(String text) {
        if (text == null) {
            return null;
        }
        return PHONE.matcher(text).replaceAll(m -> {
            String s = m.group();
            return s.substring(0, 4) + "***" + s.substring(s.length() - 3);
        });
    }

    public static void info(String message) {
        LOG.log(Level.INFO, message);
    }

    public static void warn(String message) {
        LOG.log(Level.WARNING, message);
    }

    public static void error(String message, Throwable t) {
        LOG.log(Level.SEVERE, message, t);
    }
}
