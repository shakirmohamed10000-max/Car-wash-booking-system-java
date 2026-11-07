package model;

/**
 * Life cycle of a booking.
 * <pre>
 * BOOKED -> IN_PROGRESS -> COMPLETED
 *    |            |
 *    +------------+--> CANCELLED
 * </pre>
 * COMPLETED and CANCELLED are final.
 */
public enum BookingStatus {
    BOOKED("Booked"),
    IN_PROGRESS("In progress"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled");

    private final String label;

    BookingStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** True while the vehicle is still waiting or being washed. */
    public boolean isActive() {
        return this == BOOKED || this == IN_PROGRESS;
    }

    public boolean canMoveTo(BookingStatus next) {
        return switch (this) {
            case BOOKED -> next == IN_PROGRESS || next == CANCELLED;
            case IN_PROGRESS -> next == COMPLETED || next == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
