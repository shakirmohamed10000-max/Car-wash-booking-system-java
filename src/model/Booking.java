package model;

import exception.BusinessRuleException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A wash booking. Customer name, plate, vehicle type, service names and prices are
 * snapshots, so history stays correct when the live records change.
 *
 * Every status change goes through {@link #start}, {@link #complete} or {@link #cancel},
 * which is the one place the transition rules are enforced.
 *
 * @param discountPercent reserved for the regular-customer discount (0 until that stage)
 * @param startedAt       null until the wash starts
 * @param completedAt     null unless completed
 * @param cancelledAt     null unless cancelled
 * @param cancelReason    null unless cancelled
 */
public record Booking(
        int id,
        int customerId,
        String customerName,
        int vehicleId,
        String plate,
        VehicleType vehicleType,
        List<BookingItem> items,
        long subtotal,
        int discountPercent,
        long discountAmount,
        long total,
        BookingStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Instant cancelledAt,
        String cancelReason) {

    public Booking {
        if (id <= 0 || customerId <= 0 || vehicleId <= 0) {
            throw new IllegalArgumentException("Invalid id.");
        }
        Objects.requireNonNull(customerName, "customerName");
        Objects.requireNonNull(plate, "plate");
        Objects.requireNonNull(vehicleType, "vehicleType");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("A booking needs at least one service.");
        }
        items = List.copyOf(items);
        long sum = items.stream().mapToLong(BookingItem::price).sum();
        if (sum != subtotal) {
            throw new IllegalArgumentException("Subtotal does not match the items.");
        }
        if (discountPercent < 0 || discountPercent > 100 || discountAmount < 0 || discountAmount > subtotal) {
            throw new IllegalArgumentException("Invalid discount.");
        }
        if (subtotal - discountAmount != total) {
            throw new IllegalArgumentException("Total does not match subtotal and discount.");
        }
    }

    public static String formatNumber(int id) {
        return String.format("BK-%06d", id);
    }

    public String number() {
        return formatNumber(id);
    }

    public Booking start(Instant now) {
        requireMove(BookingStatus.IN_PROGRESS);
        return copy(BookingStatus.IN_PROGRESS, now, completedAt, cancelledAt, cancelReason);
    }

    public Booking complete(Instant now) {
        requireMove(BookingStatus.COMPLETED);
        return copy(BookingStatus.COMPLETED, startedAt, now, cancelledAt, cancelReason);
    }

    public Booking cancel(Instant now, String reason) {
        requireMove(BookingStatus.CANCELLED);
        return copy(BookingStatus.CANCELLED, startedAt, completedAt, now, reason);
    }

    /** Replaces the services (and recalculates the totals). Only allowed before the wash starts. */
    public Booking withItems(List<BookingItem> newItems, int newDiscountPercent, long newDiscountAmount) {
        if (status != BookingStatus.BOOKED) {
            throw new BusinessRuleException("Services can only be changed while the booking is still waiting.");
        }
        long newSubtotal = newItems.stream().mapToLong(BookingItem::price).sum();
        return new Booking(id, customerId, customerName, vehicleId, plate, vehicleType, newItems, newSubtotal,
                newDiscountPercent, newDiscountAmount, newSubtotal - newDiscountAmount, status, createdAt,
                startedAt, completedAt, cancelledAt, cancelReason);
    }

    private void requireMove(BookingStatus next) {
        if (!status.canMoveTo(next)) {
            throw new BusinessRuleException("Booking " + number() + " is " + status.label().toLowerCase()
                    + " and cannot be changed to " + next.label().toLowerCase() + ".");
        }
    }

    private Booking copy(BookingStatus s, Instant started, Instant completed, Instant cancelled, String reason) {
        return new Booking(id, customerId, customerName, vehicleId, plate, vehicleType, items, subtotal,
                discountPercent, discountAmount, total, s, createdAt, started, completed, cancelled, reason);
    }

    @Override
    public String toString() {
        return "Booking#" + id;
    }
}
