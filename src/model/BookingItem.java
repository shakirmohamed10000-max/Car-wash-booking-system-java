package model;

import java.util.Objects;

/**
 * One service on a booking. Name, price and duration are copies taken at booking time.
 */
public record BookingItem(int serviceId, String serviceName, long price, int durationMinutes) {

    public BookingItem {
        Objects.requireNonNull(serviceName, "serviceName");
        if (serviceId <= 0 || price < 0 || durationMinutes < 0) {
            throw new IllegalArgumentException("Invalid booking item.");
        }
    }

    /** Compatibility constructor for older tests and integrations. */
    public BookingItem(int serviceId, String serviceName, long price) {
        this(serviceId, serviceName, price, 0);
    }
}
