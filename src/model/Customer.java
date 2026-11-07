package model;

import java.time.Instant;
import java.util.Objects;

/**
 * A person who brings vehicles to the wash. Immutable: every change returns a new object.
 *
 * @param phone  normalised international form, e.g. +254712345678
 * @param active false once the customer has been archived
 */
public record Customer(int id, String name, String phone, Instant createdAt, boolean active) {

    public Customer {
        if (id <= 0) {
            throw new IllegalArgumentException("Invalid customer id.");
        }
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(phone, "phone");
        Objects.requireNonNull(createdAt, "createdAt");
        if (name.isBlank() || phone.isBlank()) {
            throw new IllegalArgumentException("Name and phone are required.");
        }
    }

    public Customer withName(String newName) {
        return new Customer(id, newName, phone, createdAt, active);
    }

    public Customer withPhone(String newPhone) {
        return new Customer(id, name, newPhone, createdAt, active);
    }

    public Customer withActive(boolean newActive) {
        return new Customer(id, name, phone, createdAt, newActive);
    }

    /** Deliberately leaves out the name and phone so they cannot leak through logging. */
    @Override
    public String toString() {
        return "Customer#" + id;
    }
}
