package model;

import java.time.Instant;
import java.util.Objects;

/** One append-only payment or refund ledger entry. Card numbers are never stored. */
public record Payment(int id, int bookingId, Type type, Method method, long amount,
                      long tendered, String reference, Instant createdAt) {
    public enum Type { PAYMENT, REFUND }
    public enum Method { CASH, MPESA, CARD, REFUND }

    public Payment {
        if (id <= 0 || bookingId <= 0 || amount <= 0 || tendered < 0) {
            throw new IllegalArgumentException("Invalid payment amount or id.");
        }
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(createdAt, "createdAt");
        reference = reference == null ? "" : reference;
        if ((type == Type.PAYMENT && method == Method.REFUND)
                || (type == Type.REFUND && method != Method.REFUND)) {
            throw new IllegalArgumentException("Payment type and method do not match.");
        }
        if (type == Type.PAYMENT && method == Method.CASH && tendered < amount) {
            throw new IllegalArgumentException("Cash tendered cannot be less than the payment amount.");
        }
        if (type == Type.PAYMENT && method == Method.MPESA && !reference.matches("[A-Z0-9]{10}")) {
            throw new IllegalArgumentException("Invalid M-Pesa transaction code.");
        }
    }

    public String receiptNumber() { return String.format("RC-%06d", id); }
    public long change() { return type == Type.PAYMENT && method == Method.CASH ? tendered - amount : 0; }
}
