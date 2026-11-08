package service;

import app.Config;
import exception.ValidationException;
import java.util.Locale;
import java.util.Optional;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import model.VehicleType;

/**
 * Cleans and checks every piece of text that enters the system.
 * Each cleanXxx method returns the normalised value or throws ValidationException.
 */
public final class Validators {

    private static final Pattern NAME = Pattern.compile("^\\p{L}[\\p{L}\\p{M} '\\-]{1,59}$");
    private static final Pattern SERVICE_NAME = Pattern.compile("^[\\p{L}\\p{N} +&()\\-'./,]{2,40}$");
    private static final Pattern PLATE = Pattern.compile("^(K[A-Z]{2}|KM[A-Z]{2}|GK)(\\d{3})([A-Z])$");
    private static final Pattern NATIONAL_NUMBER = Pattern.compile("^[17]\\d{8}$");
    private static final DateTimeFormatter APPOINTMENT_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")
            .withResolverStyle(ResolverStyle.STRICT);

    private Validators() {
    }

    // ---- names and free text ----

    /** Trims, collapses spaces and checks the characters. Title-cases names typed all in one case. */
    public static String cleanName(String raw) {
        String s = collapse(raw).replace('’', '\'');
        if (s.isEmpty()) {
            throw new ValidationException("Name cannot be empty.");
        }
        if (!NAME.matcher(s).matches()) {
            throw new ValidationException(
                    "Name must be 2 to 60 characters: letters, spaces, apostrophes and hyphens only.");
        }
        // Words typed all lower or all upper are title-cased. Mixed-case words such as McDonald are left alone.
        StringBuilder sb = new StringBuilder(s.length());
        for (String word : s.split(" ")) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            boolean uniform = word.equals(word.toLowerCase(Locale.ROOT)) || word.equals(word.toUpperCase(Locale.ROOT));
            sb.append(uniform ? titleCase(word) : word);
        }
        return sb.toString();
    }

    public static String cleanServiceName(String raw) {
        String s = collapse(raw);
        if (!SERVICE_NAME.matcher(s).matches()) {
            throw new ValidationException(
                    "Service name must be 2 to 40 characters: letters, digits, spaces and + & ( ) - ' . / , only.");
        }
        return s;
    }

    /** Free text such as a cancellation reason. */
    public static String cleanText(String raw, String field, int min, int max) {
        String s = collapse(raw);
        if (s.length() < min || s.length() > max) {
            throw new ValidationException(field + " must be " + min + " to " + max + " characters.");
        }
        if (s.chars().anyMatch(Character::isISOControl)) {
            throw new ValidationException(field + " contains characters that are not allowed.");
        }
        return s;
    }

    // ---- phone ----

    /** Accepts 07XXXXXXXX, 01XXXXXXXX, +2547XXXXXXXX and 2547XXXXXXXX. Returns +254XXXXXXXXX. */
    public static String cleanPhone(String raw) {
        String s = raw == null ? "" : raw.replaceAll("[\\s\\-()]", "");
        String national;
        if (s.startsWith("+254")) {
            national = s.substring(4);
        } else if (s.startsWith("254")) {
            national = s.substring(3);
        } else if (s.startsWith("0")) {
            national = s.substring(1);
        } else {
            throw invalidPhone();
        }
        if (!NATIONAL_NUMBER.matcher(national).matches()) {
            throw invalidPhone();
        }
        return "+254" + national;
    }

    private static ValidationException invalidPhone() {
        return new ValidationException("Enter a valid Kenyan number, for example 0712345678 or +254712345678.");
    }

    /** 0712 *** 678. Used in every list so full numbers are not shown on screen by default. */
    public static String maskPhone(String stored) {
        if (stored != null && stored.matches("\\+254\\d{9}")) {
            String local = "0" + stored.substring(4);
            return local.substring(0, 4) + " *** " + local.substring(7);
        }
        return "***";
    }

    /** 0712 345 678, for the single-customer detail view. */
    public static String displayPhone(String stored) {
        if (stored != null && stored.matches("\\+254\\d{9}")) {
            String local = "0" + stored.substring(4);
            return local.substring(0, 4) + " " + local.substring(4, 7) + " " + local.substring(7);
        }
        return stored;
    }

    // ---- plates ----

    /** kaa123a becomes "KAA 123A". Accepts K__ 123A, KM__ 123A (motorbike) and GK 123A (government). */
    public static String cleanPlate(String raw) {
        String s = raw == null ? "" : raw.toUpperCase(Locale.ROOT).replaceAll("[\\s\\-]", "");
        Matcher m = PLATE.matcher(s);
        if (!m.matches()) {
            throw new ValidationException("Plate must look like KAA 123A (motorbikes: KMAA 123A).");
        }
        return m.group(1) + " " + m.group(2) + m.group(3);
    }

    /** Uppercase letters and digits only, for partial matching regardless of spacing. */
    public static String plateKey(String text) {
        return text == null ? "" : text.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    public static boolean isMotorcyclePlate(String normalisedPlate) {
        return normalisedPlate.startsWith("KM") && normalisedPlate.indexOf(' ') == 4;
    }

    /** A soft warning when the plate style and the vehicle type do not match. */
    public static Optional<String> plateTypeWarning(String normalisedPlate, VehicleType type) {
        boolean motorcycle = isMotorcyclePlate(normalisedPlate);
        if (motorcycle && type != VehicleType.BIKE) {
            return Optional.of("This is a motorcycle plate but the type is " + type.label() + ".");
        }
        if (!motorcycle && type == VehicleType.BIKE) {
            return Optional.of("Motorcycle plates normally start with KM and have four letters.");
        }
        return Optional.empty();
    }

    // ---- numbers ----

    /** Whole shillings, optionally written with commas or a KSh prefix. */
    public static long parsePrice(String raw) {
        String s = raw == null ? "" : raw.trim().replaceAll("(?i)^ksh\\s*", "").replace(",", "");
        String original = raw == null ? "" : raw.trim().replaceAll("(?i)^ksh\\s*", "");
        if (!original.matches("(?:\\d{1,9}|\\d{1,3}(?:,\\d{3})+)")) {
            throw new ValidationException("Enter the price as a whole number of shillings, for example 300.");
        }
        long value = Long.parseLong(s);
        if (value <= 0 || value > Config.MAX_PRICE) {
            throw new ValidationException("Price must be between 1 and " + Money.format(Config.MAX_PRICE) + ".");
        }
        return value;
    }

    /** Parses a strict local appointment time in the configured business timezone. */
    public static Instant parseAppointmentTime(String raw) {
        try {
            return LocalDateTime.parse(raw == null ? "" : raw.trim(), APPOINTMENT_TIME)
                    .atZone(Config.ZONE).toInstant();
        } catch (DateTimeParseException e) {
            throw new ValidationException("Use YYYY-MM-DD HH:MM and enter a real date and time.");
        }
    }

    public static int parseDuration(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (!s.matches("\\d{1,4}")) {
            throw new ValidationException("Enter the duration as whole minutes, for example 30.");
        }
        int minutes = Integer.parseInt(s);
        if (minutes < Config.MIN_DURATION_MINUTES || minutes > Config.MAX_DURATION_MINUTES) {
            throw new ValidationException("Duration must be between " + Config.MIN_DURATION_MINUTES + " and "
                    + Config.MAX_DURATION_MINUTES + " minutes.");
        }
        return minutes;
    }

    // ---- helpers ----

    private static String collapse(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
    }

    private static String titleCase(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        boolean upperNext = true;
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            sb.append(upperNext ? Character.toUpperCase(c) : c);
            upperNext = c == ' ' || c == '-' || c == '\'';
        }
        return sb.toString();
    }
}
