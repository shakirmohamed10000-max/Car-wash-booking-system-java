package service;

import app.AppLog;
import exception.BusinessRuleException;
import exception.NotFoundException;
import exception.ValidationException;
import java.time.Clock;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import model.Booking;
import model.BookingStatus;
import model.Payment;
import storage.BookingRepository;
import storage.PaymentRepository;

/** Business rules for the append-only payment ledger. */
public final class PaymentService {
    private final PaymentRepository payments;
    private final BookingRepository bookings;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, BookingRepository bookings, Clock clock) {
        this.payments = payments;
        this.bookings = bookings;
        this.clock = clock;
    }

    public Payment record(int bookingId, Payment.Method method, long amount, long tendered, String rawReference) {
        if (method == null || method == Payment.Method.REFUND) {
            throw new ValidationException("Choose Cash, M-Pesa or Card for a payment.");
        }
        Booking booking = bookings.find(bookingId).orElseThrow(() -> new NotFoundException("Booking not found."));
        if (booking.status() == BookingStatus.CANCELLED) {
            throw new BusinessRuleException("A cancelled booking cannot receive a payment.");
        }
        long due = balance(bookingId);
        if (amount <= 0 || amount > due) {
            throw new ValidationException("Payment must be from KSh 1 up to the remaining balance of "
                    + Money.format(due) + ".");
        }
        String reference = rawReference == null ? "" : rawReference.trim();
        if (method == Payment.Method.CASH) {
            if (tendered < amount) throw new ValidationException("Cash tendered must cover the payment amount.");
            reference = "";
        } else if (method == Payment.Method.MPESA) {
            reference = validateMpesaReference(reference);
            tendered = amount;
        } else if (method == Payment.Method.CARD) {
            reference = validateCardReference(reference);
            tendered = amount;
        }
        String savedReference = reference;
        long savedTendered = tendered;
        return payments.add(id -> new Payment(id, bookingId, Payment.Type.PAYMENT, method, amount, savedTendered,
                savedReference, clock.instant()));
    }

    public String validateMpesaReference(String rawReference) {
        String reference = rawReference == null ? "" : rawReference.trim().toUpperCase(Locale.ROOT);
        if (!reference.matches("[A-Z0-9]{10}")) {
            throw new ValidationException("M-Pesa transaction code must be exactly 10 letters or digits.");
        }
        if (payments.exists(p -> p.type() == Payment.Type.PAYMENT && p.method() == Payment.Method.MPESA
                && p.reference().equalsIgnoreCase(reference))) {
            throw new ValidationException("That M-Pesa transaction code has already been used.");
        }
        return reference;
    }

    public String validateCardReference(String rawReference) {
        String reference = rawReference == null ? "" : rawReference.trim();
        if (reference.length() > 40 || reference.contains("|")) {
            throw new ValidationException("Enter only an approval reference or last four digits (max 40 characters).");
        }
        if (!reference.isEmpty() && (!(reference.matches("[A-Za-z0-9-]{4,40}"))
                || (reference.matches("\\d+") && !reference.matches("\\d{4}")))) {
            throw new ValidationException("Enter an approval reference or exactly the last four digits; do not enter a card number.");
        }
        return reference;
    }

    /** Adds an audit entry for the net amount previously received; actual reversal is manual. */
    public Payment refundOnCancellation(int bookingId) {
        Booking booking = bookings.find(bookingId).orElseThrow(() -> new NotFoundException("Booking not found."));
        if (booking.status() != BookingStatus.CANCELLED) {
            throw new BusinessRuleException("A cancellation refund can only be recorded after the booking is cancelled.");
        }
        if (payments.exists(p -> p.bookingId() == bookingId && p.type() == Payment.Type.REFUND)) return null;
        long refund = paidNet(bookingId);
        if (refund <= 0) return null;
        return payments.add(id -> new Payment(id, bookingId, Payment.Type.REFUND, Payment.Method.REFUND,
                refund, refund, "Cancellation refund recorded; issue manually", clock.instant()));
    }

    /** Repairs cancellations whose refund ledger write failed or was interrupted by a crash. */
    public List<String> reconcileCancellationRefunds() {
        int failed = 0;
        for (Booking booking : bookings.all()) {
            if (booking.status() != BookingStatus.CANCELLED || paidNet(booking.id()) <= 0) continue;
            try {
                refundOnCancellation(booking.id());
            } catch (RuntimeException e) {
                failed++;
                AppLog.error("Could not reconcile cancellation refund for Booking#" + booking.id(), e);
            }
        }
        if (failed == 0) return List.of();
        List<String> warning = new ArrayList<>();
        warning.add(failed + " cancellation refund(s) are still not recorded. They will be retried at the next startup.");
        return List.copyOf(warning);
    }

    public long balance(int bookingId) {
        Booking b = bookings.find(bookingId).orElseThrow(() -> new NotFoundException("Booking not found."));
        if (b.status() == BookingStatus.CANCELLED) return 0;
        long received = payments.filter(p -> p.bookingId() == bookingId && p.type() == Payment.Type.PAYMENT)
                .stream().mapToLong(Payment::amount).sum();
        long refunded = payments.filter(p -> p.bookingId() == bookingId && p.type() == Payment.Type.REFUND)
                .stream().mapToLong(Payment::amount).sum();
        return Math.max(0, b.total() - received + refunded);
    }

    public long paidNet(int bookingId) {
        return payments.filter(p -> p.bookingId() == bookingId).stream()
                .mapToLong(p -> p.type() == Payment.Type.PAYMENT ? p.amount() : -p.amount()).sum();
    }

    public List<Payment> forBooking(int bookingId) {
        return payments.filter(p -> p.bookingId() == bookingId).stream()
                .sorted(Comparator.comparing(Payment::createdAt).thenComparingInt(Payment::id)).toList();
    }
    public List<Payment> all() {
        return payments.all().stream().sorted(Comparator.comparing(Payment::createdAt).reversed()
                .thenComparing(Comparator.comparingInt(Payment::id).reversed())).toList();
    }
    public List<Payment> allLedgerEntries() { return payments.all(); }
}
