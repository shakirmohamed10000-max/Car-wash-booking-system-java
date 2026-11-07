package app;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Central place for settings. Nothing in here is read from user input.
 */
public final class Config {

    private Config() {
    }

    /** Printed at the top of every slip. */
    public static final String BUSINESS_NAME = "Car Wash";

    public static final ZoneId ZONE = ZoneId.of("Africa/Nairobi");

    /** Rows shown per page in long lists. */
    public static final int PAGE_SIZE = 15;

    /** Longest line the input reader accepts. */
    public static final int MAX_INPUT_LENGTH = 200;

    /** Highest price (KSh) accepted for one service on one vehicle type. */
    public static final long MAX_PRICE = 100_000;

    public static final int MIN_DURATION_MINUTES = 5;
    public static final int MAX_DURATION_MINUTES = 480;

    /** Characters per line on a printed slip. */
    public static final int SLIP_WIDTH = 40;

    /**
     * Pairs of service names (lower case) that normally cover the same work.
     * Booking both only produces a warning, never a block.
     */
    public static final List<List<String>> OVERLAPPING_SERVICES = List.of(
            List.of("basic wash", "full wash + wax"),
            List.of("basic wash", "interior + exterior"),
            List.of("full wash + wax", "interior + exterior"));

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH).withZone(ZONE);

    /**
     * Data folder. Relative to the folder the program is started from.
     * The system property exists for tests and is not reachable from the terminal UI.
     */
    public static Path dataDir() {
        return Paths.get(System.getProperty("carwash.data", "data"));
    }

    public static Path logDir() {
        return Paths.get(System.getProperty("carwash.logs", "logs"));
    }

    public static Clock clock() {
        return Clock.system(ZONE);
    }

    public static String formatTime(Instant instant) {
        return instant == null ? "-" : DISPLAY_TIME.format(instant);
    }
}
