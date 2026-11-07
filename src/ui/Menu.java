package ui;

import app.AppLog;
import exception.AppException;
import java.io.PrintStream;
import java.util.List;

/**
 * Base class for menus. {@link #guard} is the single place where errors become plain messages,
 * so no stack trace or internal detail can reach the terminal.
 */
public abstract class Menu {

    /** One numbered entry in a menu. */
    public record Item(String label, Runnable action) {
    }

    protected final InputReader in;
    protected final PrintStream out;

    protected Menu(InputReader in, PrintStream out) {
        this.in = in;
        this.out = out;
    }

    protected void loop(String title, String backLabel, List<Item> items) {
        while (true) {
            out.println();
            out.println(title);
            for (int i = 0; i < items.size(); i++) {
                out.printf("  %d. %s%n", i + 1, items.get(i).label());
            }
            out.printf("  0. %s%n", backLabel);
            int choice = in.menuChoice("Choose", items.size());
            if (choice == 0) {
                return;
            }
            guard(items.get(choice - 1).action());
        }
    }

    protected void guard(Runnable action) {
        try {
            action.run();
        } catch (CancelledException e) {
            out.println("Cancelled.");
        } catch (InputClosedException e) {
            throw e;
        } catch (AppException e) {
            out.println(e.getMessage());
        } catch (RuntimeException e) {
            AppLog.error("Unexpected error", e);
            out.println("Something went wrong. Nothing was saved. Details were written to the log.");
        }
    }

    /** Prints a heading and the reminder that 0 cancels. */
    protected void begin(String heading) {
        out.println();
        out.println(heading + " (enter 0 at any prompt to cancel)");
    }
}
