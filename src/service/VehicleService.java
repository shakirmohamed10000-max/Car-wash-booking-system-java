package service;

import exception.BusinessRuleException;
import exception.DuplicateException;
import exception.NotFoundException;
import exception.ValidationException;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import model.Booking;
import model.Customer;
import model.Vehicle;
import model.VehicleType;
import storage.BookingRepository;
import storage.CustomerRepository;
import storage.VehicleRepository;

/** Business rules for vehicles. */
public final class VehicleService {

    private static final Comparator<Vehicle> BY_PLATE = Comparator.comparing(Vehicle::plate);

    private final VehicleRepository vehicles;
    private final CustomerRepository customers;
    private final BookingRepository bookings;
    private final Clock clock;

    public VehicleService(VehicleRepository vehicles, CustomerRepository customers, BookingRepository bookings,
                          Clock clock) {
        this.vehicles = vehicles;
        this.customers = customers;
        this.bookings = bookings;
        this.clock = clock;
    }

    /**
     * Registers a vehicle for an active customer. If the plate was removed earlier and archived,
     * that record is brought back with the new owner and type.
     */
    public Vehicle add(String rawPlate, VehicleType type, int customerId) {
        Customer owner = customers.find(customerId).filter(Customer::active)
                .orElseThrow(() -> new NotFoundException("No customer with ID " + customerId + "."));
        String plate = Validators.cleanPlate(rawPlate);
        var existing = vehicles.findByPlate(plate);
        if (existing.isPresent()) {
            Vehicle v = existing.get();
            if (v.active()) {
                throw new DuplicateException("Plate " + plate + " is already registered to " + ownerName(v) + ".", v.id());
            }
            Vehicle revived = v.withType(type).withCustomerId(owner.id()).withActive(true);
            vehicles.update(revived);
            return revived;
        }
        return vehicles.add(id -> new Vehicle(id, plate, type, owner.id(), clock.instant(), true));
    }

    public Vehicle get(int id) {
        return vehicles.find(id).filter(Vehicle::active)
                .orElseThrow(() -> new NotFoundException("No vehicle with ID " + id + "."));
    }

    public List<Vehicle> list() {
        return vehicles.filter(Vehicle::active).stream().sorted(BY_PLATE).toList();
    }

    public List<Vehicle> byCustomer(int customerId) {
        return vehicles.filter(v -> v.active() && v.customerId() == customerId).stream().sorted(BY_PLATE).toList();
    }

    /** Partial plate match, ignoring case and spacing. */
    public List<Vehicle> search(String query) {
        String key = Validators.plateKey(query);
        if (key.isEmpty()) {
            return List.of();
        }
        return vehicles.filter(v -> v.active() && Validators.plateKey(v.plate()).contains(key)).stream()
                .sorted(BY_PLATE).toList();
    }

    /** Name of the owner for display. Falls back to a neutral word if the record is missing. */
    public String ownerName(Vehicle v) {
        return customers.find(v.customerId()).map(Customer::name).orElse("an unknown customer");
    }

    /** True if any booking, past or present, was made for this vehicle. */
    public boolean hasHistory(int vehicleId) {
        return bookings.existsForVehicle(vehicleId);
    }

    /**
     * Changes plate, type and/or owner. A null argument keeps the current value.
     * Type and owner cannot change while the vehicle has an active booking.
     */
    public Vehicle update(int id, String rawPlate, VehicleType newType, Integer newOwnerId) {
        Vehicle current = get(id);
        Vehicle updated = current;
        if (rawPlate != null) {
            String plate = Validators.cleanPlate(rawPlate);
            var clash = vehicles.findByPlate(plate);
            if (clash.isPresent() && clash.get().id() != id) {
                throw new DuplicateException("Plate " + plate + " is already registered to "
                        + ownerName(clash.get()) + ".", clash.get().id());
            }
            updated = updated.withPlate(plate);
        }
        if (newType != null) {
            updated = updated.withType(newType);
        }
        if (newOwnerId != null) {
            Customer owner = customers.find(newOwnerId).filter(Customer::active)
                    .orElseThrow(() -> new NotFoundException("No customer with ID " + newOwnerId + "."));
            updated = updated.withCustomerId(owner.id());
        }
        if (updated.equals(current)) {
            throw new ValidationException("Nothing to change.");
        }
        if (updated.type() != current.type() || updated.customerId() != current.customerId()) {
            requireNoActiveBooking(id);
        }
        vehicles.update(updated);
        return updated;
    }

    /** Removes the vehicle, or archives it if it has booking history. Blocked while a booking is active. */
    public DeleteOutcome delete(int id) {
        Vehicle v = get(id);
        requireNoActiveBooking(id);
        if (bookings.existsForVehicle(id)) {
            vehicles.update(v.withActive(false));
            return DeleteOutcome.ARCHIVED;
        }
        vehicles.remove(id);
        return DeleteOutcome.DELETED;
    }

    private void requireNoActiveBooking(int vehicleId) {
        bookings.findActiveByVehicle(vehicleId).ifPresent((Booking b) -> {
            throw new BusinessRuleException("This vehicle has an active booking (" + b.number()
                    + "). Finish or cancel it first.");
        });
    }
}
