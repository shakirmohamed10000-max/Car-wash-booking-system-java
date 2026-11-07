package ui;

import app.Config;
import exception.ValidationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.function.Function;

/**
 * The only class that touches standard input. Every method keeps asking until the answer is
 * usable, so bad input can never crash the program. Typing 0 at any prompt that uses
 * {@link #ask} throws {@link CancelledException}; end of input throws {@link InputClosedException}.
 */
public final class InputReader {

    private final BufferedReader reader;
    private final PrintStream out;

    public InputReader(InputStream in, PrintStream out) {
        this.reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        this.out = out;
    }

    /** One line, trimmed, control characters removed. Blank is allowed. "0" cancels. */
    public String ask(String prompt) {
        String s = readLine(prompt);
        if (s.equals("0")) {
            throw new CancelledException();
        }
        return s;
    }

    /** Keeps asking until the answer is not blank. */
    public String askNonBlank(String prompt) {
        while (true) {
            String s = ask(prompt);
            if (!s.isEmpty()) {
                return s;
            }
            out.println("This cannot be empty.");
        }
    }

    /** Asks until the parser accepts the answer. The parser throws ValidationException to reject it. */
    public <T> T askValid(String prompt, Function<String, T> parser) {
        while (true) {
            String s = ask(prompt);
            try {
                return parser.apply(s);
            } catch (ValidationException e) {
                out.println(e.getMessage());
            }
        }
    }

    /** Like askValid, but a blank answer returns null (meaning "keep the current value"). */
    public <T> T askOptionalValid(String prompt, Function<String, T> parser) {
        while (true) {
            String s = ask(prompt);
            if (s.isEmpty()) {
                return null;
            }
            try {
                return parser.apply(s);
            } catch (ValidationException e) {
                out.println(e.getMessage());
            }
        }
    }

    /** A number from min to max. */
    public int askInt(String prompt, int min, int max) {
        return askValid(prompt + " (" + min + "-" + max + ")", s -> {
            if (!s.matches("\\d{1,9}")) {
                throw new ValidationException("Enter a number from " + min + " to " + max + ".");
            }
            int n = Integer.parseInt(s);
            if (n < min || n > max) {
                throw new ValidationException("Enter a number from " + min + " to " + max + ".");
            }
            return n;
        });
    }

    /** Menu choice where 0 means "back", not "cancel". */
    public int menuChoice(String prompt, int max) {
        while (true) {
            String s = readLine(prompt);
            if (s.matches("\\d{1,3}")) {
                int n = Integer.parseInt(s);
                if (n >= 0 && n <= max) {
                    return n;
                }
            }
            out.println("Enter a number from 0 to " + max + ".");
        }
    }

    /** yes/no question. Accepts y, yes, n, no. */
    public boolean askYesNo(String prompt) {
        while (true) {
            String s = ask(prompt + " (yes/no)").toLowerCase(Locale.ROOT);
            if (s.equals("y") || s.equals("yes")) {
                return true;
            }
            if (s.equals("n") || s.equals("no")) {
                return false;
            }
            out.println("Please answer yes or no.");
        }
    }

    /** For destructive actions: only the full word "yes" proceeds. Anything else, including Enter, declines. */
    public boolean confirmYes(String prompt) {
        String s = readLine(prompt + " Type yes to confirm");
        return s.equalsIgnoreCase("yes");
    }

    /** Waits for Enter. A "0" answer returns false. Used for paging. */
    public boolean pause(String prompt) {
        return !readLine(prompt).equals("0");
    }

    private String readLine(String prompt) {
        while (true) {
            out.print(prompt + ": ");
            out.flush();
            String line;
            try {
                line = reader.readLine();
            } catch (IOException e) {
                throw new InputClosedException();
            }
            if (line == null) {
                throw new InputClosedException();
            }
            String cleaned = line.replaceAll("\\p{Cntrl}", "").trim();
            if (cleaned.length() > Config.MAX_INPUT_LENGTH) {
                out.println("That is too long. The limit is " + Config.MAX_INPUT_LENGTH + " characters.");
                continue;
            }
            return cleaned;
        }
    }
}
