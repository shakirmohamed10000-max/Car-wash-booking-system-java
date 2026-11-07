package model;

import java.util.Objects;

/**
 * One service on a booking. The name and price are copies taken when the booking was made,
 * so later edits to the service catalogue never change an existing booking.
 */
public record BookingItem(int serviceId, String serviceName, long price) {

    public BookingItem {
        Objects.requireNonNull(serviceName, "serviceName");
        if (serviceId <= 0 || price < 0) {
            throw new IllegalArgumentException("Invalid booking item.");
        }
    }
}
