package ui;

import java.io.PrintStream;
import java.time.LocalDate;
import app.Config;
import java.time.format.DateTimeParseException;
import java.util.List;
import service.DailyReport;
import service.Money;
import service.ReportService;

/** Displays daily operational and payment summaries in the current text UI. */
public final class ReportsMenu extends Menu {
    private final ReportService reports;
    public ReportsMenu(InputReader in, PrintStream out, ReportService reports) {
        super(in, out);
        this.reports = reports;
    }
    public void run() {
        loop("Daily reports", "Back to main menu", List.of(
                new Item("Today's report", () -> show(LocalDate.now(Config.ZONE))),
                new Item("Report for a date", this::chooseDate)));
    }
    private void chooseDate() {
        begin("Daily report for a date");
        LocalDate date = in.askValid("Date (YYYY-MM-DD)", s -> {
            try { return LocalDate.parse(s); }
            catch (DateTimeParseException e) { throw new exception.ValidationException("Enter a valid date as YYYY-MM-DD."); }
        });
        show(date);
    }
    private void show(LocalDate date) {
        DailyReport r = reports.forDate(date);
        out.println();
        out.println("Daily report — " + r.date());
        out.println("Bookings created:       " + r.bookingsCreated());
        out.println("Washes completed:       " + r.washesCompleted());
        out.println("Cancellations:          " + r.cancellations());
        out.println("Completed wash value:   " + Money.format(r.completedWashValue()));
        out.println("Payments received:");
        out.println("  Cash:                 " + Money.format(r.cashReceived()));
        out.println("  M-Pesa:               " + Money.format(r.mpesaReceived()));
        out.println("  Card:                 " + Money.format(r.cardReceived()));
        out.println("  Total received:       " + Money.format(r.totalReceived()));
        out.println("Refunds recorded:       " + Money.format(r.refunds()));
        out.println("Net payments:           " + Money.format(r.netPayments()));
        out.println("Note: reports are based on recorded transactions and local business date.");
    }
}
