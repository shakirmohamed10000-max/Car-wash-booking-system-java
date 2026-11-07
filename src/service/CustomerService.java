package service;

import exception.BusinessRuleException;
import exception.DuplicateException;
import exception.NotFoundException;
import exception.ValidationException;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import model.Customer;
import storage.BookingRepository;
import storage.CustomerRepository;
import storage.VehicleRepository;

/** Business rules for customers. */
public final class CustomerService {

    private static final Comparator<Customer> BY_NAME =
            Comparator.comparing((Customer c) -> c.name().toLowerCase(Locale.ROOT)).thenComparingInt(Customer::id);

    private final CustomerRepository customers;
    private final VehicleRepository vehicles;
    private final BookingRepository bookings;
    private final Clock clock;

    public CustomerService(CustomerRepository customers, VehicleRepository vehicles, BookingRepository bookings,
                           Clock clock) {
        this.customers = customers;
        this.vehicles = vehicles;
        this.bookings = bookings;
        this.clock = clock;
    }

    public Customer register(String rawName, String rawPhone) {
        String name = Validators.cleanName(rawName);
        String phone = Validators.cleanPhone(rawPhone);
        Optional<Customer> existing = customers.findByPhone(phone);
        if (existing.isPresent()) {
            Customer c = existing.get();
            if (c.active()) {
                throw new DuplicateException("That number is already registered to " + c.name() + " (ID " + c.id() + ").",
                        c.id());
            }
            throw new DuplicateException("That number belongs to an archived customer (ID " + c.id() + ").", c.id());
        }
        return customers.add(id -> new Customer(id, name, phone, clock.instant(), true));
    }

    /** Brings back an archived customer with their old details and history. */
    public Customer restore(int id) {
        Customer c = customers.find(id).orElseThrow(() -> new NotFoundException("No customer with ID " + id + "."));
        if (c.active()) {
            return c;
        }
        Customer restored = c.withActive(true);
        customers.update(restored);
        return restored;
    }

    /** Active customer by id. */
    public Customer get(int id) {
        return customers.find(id).filter(Customer::active)
                .orElseThrow(() -> new NotFoundException("No customer with ID " + id + "."));
    }

    /** Customer by id including archived ones. */
    public Optional<Customer> findAny(int id) {
        return customers.find(id);
    }

    public Optional<Customer> findByPhone(String normalisedPhone) {
        return customers.findByPhone(normalisedPhone).filter(Customer::active);
    }

    public List<Customer> list() {
        return customers.filter(Customer::active).stream().sorted(BY_NAME).toList();
    }

    /** Case-insensitive match on part of the name, or on at least three digits of the phone. */
    public List<Customer> search(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return List.of();
        }
        String digits = q.replaceAll("\\D", "");
        boolean phoneSearch = digits.length() >= 3 && digits.length() >= q.replaceAll("[\\s\\-+]", "").length();
        return customers.filter(Customer::active).stream()
                .filter(c -> c.name().toLowerCase(Locale.ROOT).contains(q)
                        || (phoneSearch && c.phone().contains(digits.startsWith("0") ? digits.substring(1) : digits)))
                .sorted(BY_NAME)
                .toList();
    }

    /** Active customers with the same name, used for the "similar customer exists" warning. */
    public List<Customer> sameName(String rawName) {
        String name = Validators.cleanName(rawName);
        return customers.filter(c -> c.active() && c.name().equalsIgnoreCase(name));
    }

    /**
     * Changes the name and/or phone. A null argument keeps the current value.
     */
    public Customer update(int id, String rawName, String rawPhone) {
        Customer current = get(id);
        Customer updated = current;
        if (rawName != null) {
            updated = updated.withName(Validators.cleanName(rawName));
        }
        if (rawPhone != null) {
            String phone = Validators.cleanPhone(rawPhone);
            Optional<Customer> clash = customers.findByPhone(phone);
            if (clash.isPresent() && clash.get().id() != id) {
                throw new DuplicateException("That number is already used by another customer (ID "
                        + clash.get().id() + ").", clash.get().id());
            }
            updated = updated.withPhone(phone);
        }
        if (updated.equals(current)) {
            throw new ValidationException("Nothing to change.");
        }
        customers.update(updated);
        return updated;
    }

    /**
     * Deletes or archives a customer.
     * <ul>
     *   <li>Active vehicles or active bookings: blocked.</li>
     *   <li>Any booking history or archived vehicles: archived (hidden, kept).</li>
     *   <li>Otherwise: removed.</li>
     * </ul>
     */
    public DeleteOutcome delete(int id) {
        Customer c = get(id);
        if (bookings.existsActiveForCustomer(id)) {
            throw new BusinessRuleException("This customer has an active booking. Finish or cancel it first.");
        }
        if (vehicles.exists(v -> v.customerId() == id && v.active())) {
            throw new BusinessRuleException("This customer still has vehicles. Remove the vehicles first.");
        }
        if (bookings.existsForCustomer(id) || vehicles.exists(v -> v.customerId() == id)) {
            customers.update(c.withActive(false));
            return DeleteOutcome.ARCHIVED;
        }
        customers.remove(id);
        return DeleteOutcome.DELETED;
    }
}
