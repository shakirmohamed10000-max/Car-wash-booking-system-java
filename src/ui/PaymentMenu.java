package ui;

import java.io.PrintStream;
import app.Config;
import java.util.ArrayList;
import java.util.List;
import exception.NotFoundException;
import model.Booking;
import model.Payment;
import service.BookingService;
import service.Money;
import service.PaymentService;

/** Existing numbered-menu UI for payment and receipt operations. */
public final class PaymentMenu extends Menu {
    private final PaymentService payments;
    private final BookingService bookings;
    private final Pickers pickers;

    public PaymentMenu(InputReader in, PrintStream out, PaymentService payments, BookingService bookings, Pickers pickers) {
        super(in, out);
        this.payments = payments;
        this.bookings = bookings;
        this.pickers = pickers;
    }

    public void run() {
        loop("Payments", "Back to main menu", List.of(
                new Item("Record a payment", this::record),
                new Item("View booking balance and payment history", this::history),
                new Item("Payment receipts", this::receipts)));
    }

    private void record() {
        begin("Record a payment");
        Booking b = pickers.pickBooking();
        long balance = payments.balance(b.id());
        out.println("Booking " + b.number() + " total: " + Money.format(b.total()));
        out.println("Paid: " + Money.format(payments.paidNet(b.id())) + "  Balance: " + Money.format(balance));
        if (balance == 0) { out.println("No payment is due."); return; }
        out.println("  1. Cash\n  2. M-Pesa\n  3. Card");
        int choice = in.askInt("Payment method", 1, 3);
        Payment.Method method = Payment.Method.values()[choice - 1];
        long amount = in.askValid("Amount being paid in whole KSh", s -> parseAmount(s, balance));
        long tendered = amount;
        String reference = "";
        if (method == Payment.Method.CASH) {
            tendered = in.askValid("Cash tendered in whole KSh", s -> parseAmount(s, Long.MAX_VALUE));
        } else if (method == Payment.Method.MPESA) {
            reference = in.askValid("M-Pesa transaction code (10 letters/digits)", payments::validateMpesaReference);
        } else {
            reference = in.askValid("Card approval reference or last four digits only (never enter a full card number)",
                    payments::validateCardReference);
        }
        Payment p = payments.record(b.id(), method, amount, tendered, reference);
        printReceipt(p, b);
        out.println("Remaining balance: " + Money.format(payments.balance(b.id())));
    }

    private void history() {
        begin("Booking payment history");
        Booking b = pickers.pickBooking();
        out.println("Booking " + b.number() + " total: " + Money.format(b.total()));
        out.println("Net paid: " + Money.format(payments.paidNet(b.id())));
        out.println("Balance: " + Money.format(payments.balance(b.id())));
        List<Payment> list = payments.forBooking(b.id());
        if (list.isEmpty()) { out.println("No payments recorded."); return; }
        List<String[]> rows = new ArrayList<>();
        for (Payment p : list) rows.add(new String[] {p.receiptNumber(), p.type().name(), p.method().name(),
                Money.format(p.amount()), p.reference().isBlank() ? "-" : p.reference()});
        new TablePrinter(out, in).print(new String[] {"Receipt", "Type", "Method", "Amount", "Reference"}, rows);
    }

    private void receipts() {
        begin("Payment receipts");
        List<Payment> list = payments.all();
        if (list.isEmpty()) { out.println("No payment receipts yet."); return; }
        for (Payment p : list) {
            out.printf("%s  %s  %s  %s%n", p.receiptNumber(), p.type(), Money.format(p.amount()),
                    p.createdAt().toString());
        }
        String ref = in.askNonBlank("Receipt number (e.g. RC-000001)").toUpperCase();
        Payment p = list.stream().filter(x -> x.receiptNumber().equals(ref)).findFirst()
                .orElseThrow(() -> new exception.NotFoundException("No receipt with that number."));
        try {
            Booking b = bookings.getById(p.bookingId());
            printReceipt(p, b);
        } catch (NotFoundException e) {
            printLedgerOnlyReceipt(p);
        }
    }

    private void printLedgerOnlyReceipt(Payment p) {
        out.println();
        out.println("========================================");
        out.println("             CAR WASH");
        out.println("Payment receipt " + p.receiptNumber());
        out.println("Booking: " + Booking.formatNumber(p.bookingId()) + " (booking data unavailable)");
        out.println("Type: " + p.type() + "   Method: " + p.method());
        out.println("Amount: " + Money.format(p.amount()));
        if (!p.reference().isBlank()) out.println("Reference: " + p.reference());
        out.println("Recorded: " + Config.formatTime(p.createdAt()));
        out.println("Original booking details are missing; this receipt is from the payment ledger.");
        out.println("========================================");
    }

    private static long parseAmount(String text, long max) {
        if (!text.matches("\\d{1,12}")) throw new exception.ValidationException("Enter a whole-shilling amount.");
        long n = Long.parseLong(text);
        if (n < 1 || n > max) throw new exception.ValidationException("Enter an amount from KSh 1 up to " + Money.format(max) + ".");
        return n;
    }

    private void printReceipt(Payment p, Booking b) {
        out.println();
        out.println("========================================");
        out.println("             CAR WASH");
        out.println("Payment receipt " + p.receiptNumber());
        out.println("Booking: " + b.number() + "   Vehicle: " + b.plate());
        out.println("Type: " + p.type() + "   Method: " + p.method());
        out.println("Amount: " + Money.format(p.amount()));
        if (p.method() == Payment.Method.CASH && p.type() == Payment.Type.PAYMENT) {
            out.println("Tendered: " + Money.format(p.tendered()));
            out.println("Change: " + Money.format(p.change()));
        }
        if (!p.reference().isBlank()) out.println("Reference: " + p.reference());
        out.println("Recorded: " + Config.formatTime(p.createdAt()));
        if (p.type() == Payment.Type.REFUND) out.println("Note: Refund entry recorded; issue the refund manually.");
        out.println("========================================");
    }
}
