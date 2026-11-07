package ui;

import java.io.PrintStream;
import java.util.List;
import model.Customer;
import model.Vehicle;
import model.VehicleType;
import service.CustomerService;
import service.DeleteOutcome;
import service.Validators;
import service.VehicleService;

/** Vehicle menu: add, list, search, view, update, delete. */
public final class VehicleMenu extends Menu {

    private final VehicleService vehicles;
    private final CustomerService customers;
    private final Views views;
    private final Pickers pickers;

    public VehicleMenu(InputReader in, PrintStream out, VehicleService vehicles, CustomerService customers,
                       Views views, Pickers pickers) {
        super(in, out);
        this.vehicles = vehicles;
        this.customers = customers;
        this.views = views;
        this.pickers = pickers;
    }

    public void run() {
        loop("Vehicles", "Back to main menu", List.of(
                new Item("Add vehicle", this::add),
                new Item("List all vehicles", () -> views.vehicleList(vehicles.list())),
                new Item("List vehicles of a customer", () -> {
                    begin("Vehicles of a customer");
                    Customer c = pickers.pickCustomer(false);
                    views.vehicleList(vehicles.byCustomer(c.id()));
                }),
                new Item("Search by plate", () -> {
                    begin("Search by plate");
                    views.vehicleList(vehicles.search(in.askNonBlank("Plate or part of a plate")));
                }),
                new Item("View vehicle", () -> {
                    begin("View vehicle");
                    views.vehicleDetail(pickers.pickVehicle());
                }),
                new Item("Update vehicle", this::update),
                new Item("Delete vehicle", this::delete)));
    }

    private void add() {
        begin("Add vehicle");
        if (customers.list().isEmpty()) {
            if (!in.askYesNo("No customers are registered yet. Register one now")) {
                throw new CancelledException();
            }
        }
        Customer owner = pickers.pickCustomer(true);
        pickers.registerVehicle(owner);
    }

    private void update() {
        begin("Update vehicle");
        Vehicle v = pickers.pickVehicle();
        out.println("Press Enter to keep the current value.");
        String plate = in.askOptionalValid("Plate [" + v.plate() + "]", Validators::cleanPlate);
        if (plate != null && plate.equals(v.plate())) {
            plate = null;
        }
        if (plate != null && vehicles.hasHistory(v.id())
                && !in.askYesNo("This vehicle has booking history. Old bookings keep the old plate. Change it")) {
            throw new CancelledException();
        }
        out.println("Types: 1 Bike, 2 Car, 3 SUV, 4 Truck");
        VehicleType type = in.askOptionalValid("Type [" + v.type().label() + "]", Pickers::parseTypeChoice);
        if (type == v.type()) {
            type = null;
        }
        Integer ownerId = null;
        if (in.askYesNo("Transfer to another owner (currently " + vehicles.ownerName(v) + ")")) {
            ownerId = pickers.pickCustomer(false).id();
            if (ownerId == v.customerId()) {
                ownerId = null;
            }
        }
        if (plate == null && type == null && ownerId == null) {
            out.println("Nothing changed.");
            return;
        }
        Vehicle updated = vehicles.update(v.id(), plate, type, ownerId);
        out.println("Vehicle updated: " + updated.plate() + ".");
    }

    private void delete() {
        begin("Delete vehicle");
        Vehicle v = pickers.pickVehicle();
        views.vehicleDetail(v);
        out.println();
        if (!in.confirmYes("Delete " + v.plate() + "?")) {
            out.println("Nothing was deleted.");
            return;
        }
        DeleteOutcome result = vehicles.delete(v.id());
        if (result == DeleteOutcome.ARCHIVED) {
            out.println("Vehicle has booking history, so the record was archived instead of deleted.");
        } else {
            out.println("Vehicle deleted.");
        }
    }
}
