package ui;

import exception.ValidationException;
import java.io.PrintStream;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import model.VehicleType;
import model.WashService;
import service.ServiceCatalog;
import service.Validators;

/** Wash service menu: list, view, add, edit, activate or deactivate, delete. */
public final class ServiceMenu extends Menu {

    /** Returned by the price prompt when the attendant types a dash. */
    private static final long REMOVE = -1;

    private final ServiceCatalog catalog;
    private final Views views;

    public ServiceMenu(InputReader in, PrintStream out, ServiceCatalog catalog, Views views) {
        super(in, out);
        this.catalog = catalog;
        this.views = views;
    }

    public void run() {
        loop("Wash services", "Back to main menu", List.of(
                new Item("List services", () -> views.serviceList(catalog.listAll())),
                new Item("Add service", this::add),
                new Item("Edit service", this::edit),
                new Item("Activate or deactivate service", this::toggle),
                new Item("Delete service", this::delete)));
    }

    private WashService pick() {
        views.serviceList(catalog.listAll());
        return catalog.get(in.askInt("Service ID", 1, Integer.MAX_VALUE));
    }

    private void add() {
        begin("Add service");
        String name = in.askValid("Service name", Validators::cleanServiceName);
        int minutes = in.askValid("Duration in minutes", Validators::parseDuration);
        out.println("Enter a price in KSh for each vehicle type, or press Enter if it is not offered.");
        Map<VehicleType, Long> prices = new EnumMap<>(VehicleType.class);
        for (VehicleType t : VehicleType.values()) {
            Long p = in.askOptionalValid("Price for " + t.label(), Validators::parsePrice);
            if (p != null) {
                prices.put(t, p);
            }
        }
        WashService s = catalog.create(name, minutes, prices);
        out.println("Service saved. ID " + s.id() + ".");
    }

    private void edit() {
        begin("Edit service");
        WashService s = pick();
        out.println("Press Enter to keep the current value. Type - for a price to stop offering it.");
        String name = in.askOptionalValid("Name [" + s.name() + "]", Validators::cleanServiceName);
        Integer minutes = in.askOptionalValid("Minutes [" + s.durationMinutes() + "]", Validators::parseDuration);
        Map<VehicleType, Long> prices = new EnumMap<>(VehicleType.class);
        for (VehicleType t : VehicleType.values()) {
            Long current = s.priceFor(t).isPresent() ? s.priceFor(t).getAsLong() : null;
            Long answer = in.askOptionalValid("Price for " + t.label() + " ["
                    + (current == null ? "not offered" : current) + "]", text -> {
                        if (text.equals("-")) {
                            return REMOVE;
                        }
                        return Validators.parsePrice(text);
                    });
            if (answer == null) {
                if (current != null) {
                    prices.put(t, current);
                }
            } else if (answer != REMOVE) {
                prices.put(t, answer);
            }
        }
        if (prices.isEmpty()) {
            throw new ValidationException("A service needs a price for at least one vehicle type.");
        }
        catalog.update(s.id(), name, minutes, prices.equals(s.prices()) ? null : prices);
        out.println("Service updated. New prices apply to new bookings only.");
    }

    private void toggle() {
        begin("Activate or deactivate service");
        WashService s = pick();
        boolean makeActive = !s.active();
        if (!in.askYesNo((makeActive ? "Activate " : "Deactivate ") + s.name())) {
            out.println("No change made.");
            return;
        }
        catalog.setActive(s.id(), makeActive);
        out.println(makeActive ? "Service is now active." : "Service is now hidden from new bookings.");
    }

    private void delete() {
        begin("Delete service");
        WashService s = pick();
        if (!in.confirmYes("Delete " + s.name() + "?")) {
            out.println("Nothing was deleted.");
            return;
        }
        catalog.delete(s.id());
        out.println("Service deleted.");
    }
}
