package service;

import app.Config;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import model.Booking;
import model.BookingStatus;
import model.Payment;
import storage.BookingRepository;
import storage.PaymentRepository;

/**
 * Decides whether a customer is a "regular" and therefore gets a percentage off new bookings.
 *
 * <p>Security rules built into the design:
 * <ul>
 *   <li><b>The percentage is never typed in.</b> It is calculated here from saved history and the
 *       fixed values in {@link Config}. No menu can set or override it.</li>
 *   <li><b>Only real visits count.</b> A wash qualifies only when the booking is COMPLETED, its total
 *       reached the minimum, and it has been paid in full (payments minus refunds). Cancelled bookings,
 *       unpaid bookings and tiny bookings made just to collect visits are ignored.</li>
 *   <li><b>Identity is the customer id</b>, not the name or phone, so renaming or changing a phone number
 *       neither gains nor loses the discount.</li>
 *   <li><b>Fail closed.</b> Missing payment data means no discount, never a free one.</li>
 *   <li>The value is stored on each booking as a snapshot, so later rule changes never rewrite old bills.</li>
 * </ul>
 */
public final class LoyaltyService {

    /**
     * @param qualifyingWashes paid, completed washes counted so far
     * @param percent          discount a new booking gets right now (0 if not a regular yet)
     * @param washesNeeded     how many qualifying washes make a regular
     */
    public record Status(int qualifyingWashes, int percent, int washesNeeded) {

        public boolean regular() {
            return percent > 0;
        }

        public int washesToGo() {
            return Math.max(0, washesNeeded - qualifyingWashes);
        }
    }

    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final int minWashes;
    private final int percent;
    private final long minVisitTotal;

    /** Uses the fixed values from {@link Config}. */
    public LoyaltyService(BookingRepository bookings, PaymentRepository payments) {
        this(bookings, payments, Config.LOYALTY_MIN_WASHES, Config.LOYALTY_DISCOUNT_PERCENT,
                Config.LOYALTY_MIN_VISIT_TOTAL);
    }

    /** Explicit values, used by the tests. Unsafe values are refused outright. */
    public LoyaltyService(BookingRepository bookings, PaymentRepository payments,
                          int minWashes, int percent, long minVisitTotal) {
        this.bookings = Objects.requireNonNull(bookings, "bookings");
        this.payments = Objects.requireNonNull(payments, "payments");
        if (minWashes < 1 || minWashes > 1_000) {
            throw new IllegalArgumentException("Loyalty: washes needed must be from 1 to 1000.");
        }
        if (percent < 0 || percent > Config.LOYALTY_MAX_PERCENT) {
            throw new IllegalArgumentException("Loyalty: percentage must be from 0 to "
                    + Config.LOYALTY_MAX_PERCENT + ".");
        }
        if (minVisitTotal < 1) {
            throw new IllegalArgumentException("Loyalty: minimum visit total must be at least 1.");
        }
        this.minWashes = minWashes;
        this.percent = percent;
        this.minVisitTotal = minVisitTotal;
    }

    /** Current standing of one customer. Cheap enough to call on every booking. */
    public Status statusFor(int customerId) {
        Map<Integer, Long> netPaid = netPaidByBooking();
        int count = 0;
        for (Booking b : bookings.all()) {
            if (b.customerId() == customerId && qualifies(b, netPaid.getOrDefault(b.id(), 0L))) {
                count++;
            }
        }
        int earned = percent > 0 && count >= minWashes ? percent : 0;
        return new Status(count, earned, minWashes);
    }

    /** Discount percentage a new booking for this customer gets right now. */
    public int percentFor(int customerId) {
        return statusFor(customerId).percent();
    }

    private boolean qualifies(Booking b, long netPaid) {
        return b.status() == BookingStatus.COMPLETED
                && b.total() >= minVisitTotal
                && netPaid >= b.total();
    }

    /** Payments minus refunds, per booking, worked out once per lookup. */
    private Map<Integer, Long> netPaidByBooking() {
        Map<Integer, Long> net = new HashMap<>();
        for (Payment p : payments.all()) {
            long signed = p.type() == Payment.Type.PAYMENT ? p.amount() : -p.amount();
            net.merge(p.bookingId(), signed, Long::sum);
        }
        return net;
    }
}
