package ui;

import exception.DuplicateException;
import exception.NotFoundException;
import exception.ValidationException;
import java.io.PrintStream;
import java.util.List;
import java.util.Optional;
import model.Booking;
import model.Customer;
import model.Vehicle;
import model.VehicleType;
import service.BookingService;
import service.CustomerService;
import service.Validators;
import service.VehicleService;

/**
 * Reusable "find a record" and "create a record" prompts shared by several menus, so
 * a booking can register a customer or vehicle on the spot without duplicating any code.
 */
public final class Pickers {

    private final InputReader in;
    private final PrintStream out;
    private final Views views;
    private final CustomerService customers;
    private final VehicleService vehicles;
    private final BookingService bookings;

    public Pickers(InputReader in, PrintStream out, Views views, CustomerService customers, VehicleService vehicles,
                   BookingService bookings) {
        this.in = in;
        this.out = out;
        this.views = views;
        this.customers = customers;
        this.vehicles = vehicles;
        this.bookings = bookings;
    }

    // ---- customers ----

    /** Finds a customer by ID, phone number or part of a name. */
    public Customer pickCustomer(boolean offerRegister) {
        String q = in.askNonBlank("Customer ID, phone or name");
        if (q.matches("[1-9]\\d{0,6}")) {
            return customers.get(Integer.parseInt(q));
        }
        String phone = null;
        try {
            phone = Validators.cleanPhone(q);
            Optional<Customer> byPhone = customers.findByPhone(phone);
            if (byPhone.isPresent()) {
                return byPhone.get();
            }
        } catch (ValidationException notAPhone) {
            phone = null;
        }
        List<Customer> hits = phone == null ? customers.search(q) : List.of();
        if (hits.size() == 1) {
            return hits.get(0);
        }
        if (hits.size() > 1) {
            views.customerList(hits);
            int id = in.askInt("Customer ID from the list", 1, Integer.MAX_VALUE);
            return hits.stream().filter(c -> c.id() == id).findFirst()
                    .orElseThrow(() -> new NotFoundException("That ID is not in the list."));
        }
        if (offerRegister && in.askYesNo("No matching customer. Register a new one")) {
            return registerCustomer(phone);
        }
        throw new NotFoundException("No matching customer found.");
    }

    /**
     * Registers a customer.
     *
     * @param knownPhone a phone number already typed by the attendant, or null to ask for it
     */
    public Customer registerCustomer(String knownPhone) {
        String name = in.askValid("Name", Validators::cleanName);
        String phone = knownPhone != null ? knownPhone : in.askValid("Phone", Validators::cleanPhone);
        List<Customer> same = customers.sameName(name);
        if (!same.isEmpty() && !in.askYesNo("A customer named " + same.get(0).name() + " already exists (ID "
                + same.get(0).id() + "). Register another one")) {
            throw new CancelledException();
        }
        try {
            Customer c = customers.register(name, phone);
            out.println("Customer saved. ID " + c.id() + ".");
            return c;
        } catch (DuplicateException e) {
            Optional<Customer> existing = customers.findAny(e.existingId());
            if (existing.isPresent() && !existing.get().active()
                    && in.askYesNo(e.getMessage() + " Restore that customer")) {
                Customer c = customers.restore(existing.get().id());
                out.println("Customer restored. ID " + c.id() + ".");
                return c;
            }
            throw e;
        }
    }

    // ---- vehicles ----

    /** Finds a vehicle by ID or part of a plate. */
    public Vehicle pickVehicle() {
        String q = in.askNonBlank("Vehicle ID or plate");
        if (q.matches("[1-9]\\d{0,6}")) {
            return vehicles.get(Integer.parseInt(q));
        }
        List<Vehicle> hits = vehicles.search(q);
        if (hits.isEmpty()) {
            throw new NotFoundException("No vehicle found for that plate.");
        }
        if (hits.size() == 1) {
            return hits.get(0);
        }
        views.vehicleList(hits);
        int id = in.askInt("Vehicle ID from the list", 1, Integer.MAX_VALUE);
        return hits.stream().filter(v -> v.id() == id).findFirst()
                .orElseThrow(() -> new NotFoundException("That ID is not in the list."));
    }

    public VehicleType pickType() {
        VehicleType[] types = VehicleType.values();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < types.length; i++) {
            sb.append(i + 1).append(' ').append(types[i].label()).append(i < types.length - 1 ? ", " : "");
        }
        out.println("Types: " + sb);
        return types[in.askInt("Type", 1, types.length) - 1];
    }

    /** Parses the answer of an "Enter to keep" type prompt. */
    public static VehicleType parseTypeChoice(String s) {
        VehicleType[] types = VehicleType.values();
        if (!s.matches("[1-9]") || Integer.parseInt(s) > types.length) {
            throw new ValidationException("Enter a number from 1 to " + types.length + ".");
        }
        return types[Integer.parseInt(s) - 1];
    }

    public Vehicle registerVehicle(Customer owner) {
        String plate = in.askValid("Plate (e.g. KAA 123A)", Validators::cleanPlate);
        VehicleType type = pickType();
        Optional<String> warning = Validators.plateTypeWarning(plate, type);
        if (warning.isPresent() && !in.askYesNo(warning.get() + " Save anyway")) {
            throw new CancelledException();
        }
        Vehicle v = vehicles.add(plate, type, owner.id());
        out.println("Vehicle saved. ID " + v.id() + ", " + v.plate() + ".");
        return v;
    }

    /** Lists the customer's vehicles and lets the attendant pick one or add a new one. */
    public Vehicle pickCustomerVehicle(Customer owner) {
        List<Vehicle> owned = vehicles.byCustomer(owner.id());
        if (owned.isEmpty()) {
            if (in.askYesNo(owner.name() + " has no vehicles. Add one now")) {
                return registerVehicle(owner);
            }
            throw new CancelledException();
        }
        views.vehicleList(owned);
        while (true) {
            String s = in.askNonBlank("Vehicle ID, or N to add a new vehicle");
            if (s.equalsIgnoreCase("n")) {
                return registerVehicle(owner);
            }
            if (s.matches("[1-9]\\d{0,6}")) {
                int id = Integer.parseInt(s);
                Optional<Vehicle> chosen = owned.stream().filter(v -> v.id() == id).findFirst();
                if (chosen.isPresent()) {
                    return chosen.get();
                }
            }
            out.println("Choose an ID from the list above.");
        }
    }

    // ---- bookings ----

    public Booking pickBooking() {
        return bookings.get(in.askNonBlank("Booking number (e.g. BK-000012 or 12)"));
    }
}
