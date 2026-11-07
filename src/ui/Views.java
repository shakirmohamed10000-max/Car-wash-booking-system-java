package ui;

import app.Config;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import model.Booking;
import model.Customer;
import model.Vehicle;
import model.VehicleType;
import model.WashService;
import service.BookingService;
import service.CustomerService;
import service.Money;
import service.Validators;
import service.VehicleService;

/** Everything that prints lists and detail screens, so the menus only deal with choices. */
public final class Views {

    private final PrintStream out;
    private final TablePrinter table;
    private final CustomerService customers;
    private final VehicleService vehicles;
    private final BookingService bookings;

    public Views(PrintStream out, TablePrinter table, CustomerService customers, VehicleService vehicles,
                 BookingService bookings) {
        this.out = out;
        this.table = table;
        this.customers = customers;
        this.vehicles = vehicles;
        this.bookings = bookings;
    }

    // ---- customers ----

    public void customerList(List<Customer> list) {
        if (list.isEmpty()) {
            out.println("No customers found.");
            return;
        }
        List<String[]> rows = new ArrayList<>();
        for (Customer c : list) {
            rows.add(new String[] {
                    String.valueOf(c.id()),
                    c.name(),
                    Validators.maskPhone(c.phone()),
                    String.valueOf(vehicles.byCustomer(c.id()).size()),
                    Config.formatTime(c.createdAt()) });
        }
        table.print(new String[] {"ID", "Name", "Phone", "Vehicles", "Registered"}, rows);
    }

    /** The one place a full phone number is shown. */
    public void customerDetail(Customer c) {
        List<Vehicle> owned = vehicles.byCustomer(c.id());
        List<Booking> history = bookings.forCustomer(c.id());
        long active = history.stream().filter(b -> b.status().isActive()).count();
        out.println();
        out.println("Customer " + c.id());
        out.println("  Name:        " + c.name());
        out.println("  Phone:       " + Validators.displayPhone(c.phone()));
        out.println("  Registered:  " + Config.formatTime(c.createdAt()));
        if (owned.isEmpty()) {
            out.println("  Vehicles:    none");
        } else {
            StringBuilder sb = new StringBuilder();
            for (Vehicle v : owned) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(v.plate()).append(" (").append(v.type().label()).append(')');
            }
            out.println("  Vehicles:    " + sb);
        }
        out.println("  Bookings:    " + history.size() + (active > 0 ? " (" + active + " active)" : ""));
    }

    // ---- vehicles ----

    public void vehicleList(List<Vehicle> list) {
        if (list.isEmpty()) {
            out.println("No vehicles found.");
            return;
        }
        List<String[]> rows = new ArrayList<>();
        for (Vehicle v : list) {
            rows.add(new String[] {String.valueOf(v.id()), v.plate(), v.type().label(), vehicles.ownerName(v)});
        }
        table.print(new String[] {"ID", "Plate", "Type", "Owner"}, rows);
    }

    public void vehicleDetail(Vehicle v) {
        out.println();
        out.println("Vehicle " + v.id());
        out.println("  Plate:       " + v.plate());
        out.println("  Type:        " + v.type().label());
        out.println("  Owner:       " + vehicles.ownerName(v) + " (customer " + v.customerId() + ")");
        out.println("  Registered:  " + Config.formatTime(v.createdAt()));
        out.println("  Bookings:    " + bookings.forVehicle(v.id()).size());
    }

    // ---- services ----

    public void serviceList(List<WashService> list) {
        if (list.isEmpty()) {
            out.println("No services found.");
            return;
        }
        List<String[]> rows = new ArrayList<>();
        for (WashService s : list) {
            rows.add(new String[] {
                    String.valueOf(s.id()), s.name(), String.valueOf(s.durationMinutes()),
                    price(s, VehicleType.BIKE), price(s, VehicleType.CAR), price(s, VehicleType.SUV),
                    price(s, VehicleType.TRUCK), s.active() ? "Active" : "Inactive" });
        }
        table.print(new String[] {"ID", "Service", "Min", "Bike", "Car", "SUV", "Truck", "Status"},
                new boolean[] {false, false, true, true, true, true, true, false}, rows);
        out.println("A dash means the service is not offered for that vehicle type. Prices are in KSh.");
    }

    private static String price(WashService s, VehicleType type) {
        return s.priceFor(type).isPresent() ? String.format("%,d", s.priceFor(type).getAsLong()) : "-";
    }

    /** Numbered list of what can be booked for one vehicle type. */
    public void offered(List<WashService> list, VehicleType type) {
        List<String[]> rows = new ArrayList<>();
        int n = 1;
        for (WashService s : list) {
            rows.add(new String[] {String.valueOf(n++), s.name(), s.durationMinutes() + " min",
                    Money.format(s.priceFor(type).getAsLong())});
        }
        table.print(new String[] {"No.", "Service", "Time", "Price"}, new boolean[] {false, false, true, true}, rows);
    }

    // ---- bookings ----

    public void bookingList(List<Booking> list) {
        if (list.isEmpty()) {
            out.println("No bookings found.");
            return;
        }
        List<String[]> rows = new ArrayList<>();
        for (Booking b : list) {
            rows.add(new String[] {b.number(), Config.formatTime(b.createdAt()), b.plate(), b.customerName(),
                    String.valueOf(b.items().size()), Money.format(b.total()), b.status().label()});
        }
        table.print(new String[] {"Booking", "Created", "Plate", "Customer", "Items", "Total", "Status"},
                new boolean[] {false, false, false, false, true, true, false}, rows);
    }
}
