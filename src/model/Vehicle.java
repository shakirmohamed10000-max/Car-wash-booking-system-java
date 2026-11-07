package model;

import java.time.Instant;
import java.util.Objects;

/**
 * A vehicle owned by one customer. The owner is stored by id, not by name,
 * so renaming a customer never breaks the link.
 *
 * @param plate normalised plate, e.g. "KAA 123A"
 */
public record Vehicle(int id, String plate, VehicleType type, int customerId, Instant createdAt, boolean active) {

    public Vehicle {
        if (id <= 0 || customerId <= 0) {
            throw new IllegalArgumentException("Invalid id.");
        }
        Objects.requireNonNull(plate, "plate");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(createdAt, "createdAt");
        if (plate.isBlank()) {
            throw new IllegalArgumentException("Plate is required.");
        }
    }

    public Vehicle withPlate(String newPlate) {
        return new Vehicle(id, newPlate, type, customerId, createdAt, active);
    }

    public Vehicle withType(VehicleType newType) {
        return new Vehicle(id, plate, newType, customerId, createdAt, active);
    }

    public Vehicle withCustomerId(int newCustomerId) {
        return new Vehicle(id, plate, type, newCustomerId, createdAt, active);
    }

    public Vehicle withActive(boolean newActive) {
        return new Vehicle(id, plate, type, customerId, createdAt, newActive);
    }

    @Override
    public String toString() {
        return "Vehicle#" + id;
    }
}
