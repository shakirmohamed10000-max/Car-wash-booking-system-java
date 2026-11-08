package ui;

import app.Config;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import model.Booking;
import model.BookingItem;
import model.BookingStatus;
import service.Money;

/**
 * Plain-text slips. The title depends on the status: a waiting or running booking prints a
 * booking slip, a finished one a service summary, a cancelled one a cancellation slip.
 * Phone numbers are never printed on a slip.
 */
public final class ReceiptPrinter {

    private static final int W = Config.SLIP_WIDTH;

    private ReceiptPrinter() {
    }

    public static void print(PrintStream out, Booking b) {
        render(b).forEach(out::println);
    }

    public static List<String> render(Booking b) {
        List<String> lines = new ArrayList<>();
        String rule = "-".repeat(W);
        lines.add("");
        lines.add(center(Config.BUSINESS_NAME.toUpperCase()));
        lines.add(center(title(b.status())));
        lines.add(rule);
        lines.add(row("Booking", b.number()));
        lines.add(row("Date", Config.formatTime(b.createdAt())));
        if (b.scheduledAt() != null) {
            lines.add(row("Appointment", Config.formatTime(b.scheduledAt())));
        }
        lines.add(row("Customer", b.customerName()));
        lines.add(row("Vehicle", b.plate() + " (" + b.vehicleType().label() + ")"));
        lines.add(row("Status", b.status().label()));
        if (b.startedAt() != null) {
            lines.add(row("Started", Config.formatTime(b.startedAt())));
        }
        if (b.completedAt() != null) {
            lines.add(row("Completed", Config.formatTime(b.completedAt())));
        }
        if (b.cancelledAt() != null) {
            lines.add(row("Cancelled", Config.formatTime(b.cancelledAt())));
            lines.add(row("Reason", b.cancelReason() == null ? "-" : b.cancelReason()));
        }
        lines.add(rule);
        for (BookingItem item : b.items()) {
            lines.add(amountRow(item.serviceName(), Money.format(item.price())));
        }
        lines.add(rule);
        lines.add(amountRow("Subtotal", Money.format(b.subtotal())));
        if (b.discountAmount() > 0) {
            lines.add(amountRow("Discount (" + b.discountPercent() + "%)", "-" + Money.format(b.discountAmount())));
        }
        lines.add(amountRow(b.status() == BookingStatus.CANCELLED ? "Total (not charged)" : "TOTAL",
                Money.format(b.total())));
        lines.add(rule);
        return lines;
    }

    private static String title(BookingStatus status) {
        return switch (status) {
            case BOOKED, IN_PROGRESS -> "BOOKING SLIP";
            case COMPLETED -> "SERVICE SUMMARY";
            case CANCELLED -> "CANCELLATION SLIP";
        };
    }

    private static String center(String text) {
        if (text.length() >= W) {
            return text;
        }
        return " ".repeat((W - text.length()) / 2) + text;
    }

    /** Label on the left, value filling the rest of the line. */
    private static String row(String label, String value) {
        String left = String.format("%-10s", label);
        int room = W - left.length();
        return left + fit(value, room);
    }

    /** Label on the left, amount right-aligned. */
    private static String amountRow(String label, String amount) {
        int room = W - amount.length() - 1;
        String left = fit(label, room);
        return left + " ".repeat(W - left.length() - amount.length()) + amount;
    }

    private static String fit(String text, int room) {
        return text.length() <= room ? text : text.substring(0, Math.max(0, room - 3)) + "...";
    }
}
