package service;

import exception.BusinessRuleException;
import exception.DuplicateException;
import exception.NotFoundException;
import exception.ValidationException;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import model.VehicleType;
import model.WashService;
import storage.BookingRepository;
import storage.ServiceRepository;

/** Business rules for the wash service catalogue and its per-vehicle-type prices. */
public final class ServiceCatalog {

    private final ServiceRepository services;
    private final BookingRepository bookings;

    public ServiceCatalog(ServiceRepository services, BookingRepository bookings) {
        this.services = services;
        this.bookings = bookings;
    }

    /** Adds the four standard services, but only when the services file did not exist before this run. */
    public void seedDefaultsIfFirstRun() {
        if (!services.wasCreatedFresh()) {
            return;
        }
        seed("Basic Wash", 30, 100, 300, 400, 600);
        seed("Full Wash + Wax", 90, 200, 600, 800, 1200);
        seed("Interior + Exterior", 60, 0, 500, 650, 900);
        seed("Engine Wash", 30, 0, 400, 500, 800);
    }

    private void seed(String name, int minutes, long bike, long car, long suv, long truck) {
        Map<VehicleType, Long> prices = new EnumMap<>(VehicleType.class);
        if (bike > 0) {
            prices.put(VehicleType.BIKE, bike);
        }
        prices.put(VehicleType.CAR, car);
        prices.put(VehicleType.SUV, suv);
        prices.put(VehicleType.TRUCK, truck);
        services.add(id -> new WashService(id, name, minutes, true, prices));
    }

    public WashService get(int id) {
        return services.find(id).orElseThrow(() -> new NotFoundException("No service with ID " + id + "."));
    }

    public List<WashService> listAll() {
        return services.all().stream().sorted(Comparator.comparingInt(WashService::id)).toList();
    }

    public List<WashService> listActive() {
        return listAll().stream().filter(WashService::active).toList();
    }

    /** Active services that have a price for this vehicle type. */
    public List<WashService> offeredFor(VehicleType type) {
        return listActive().stream().filter(s -> s.offeredFor(type)).toList();
    }

    public WashService create(String rawName, int durationMinutes, Map<VehicleType, Long> prices) {
        String name = Validators.cleanServiceName(rawName);
        checkDuration(durationMinutes);
        checkPrices(prices);
        requireUniqueName(name, -1);
        return services.add(id -> new WashService(id, name, durationMinutes, true, prices));
    }

    /**
     * Changes a service. A null argument keeps the current value; a prices map replaces all prices.
     * Existing bookings keep the prices they were made with.
     */
    public WashService update(int id, String rawName, Integer durationMinutes, Map<VehicleType, Long> prices) {
        WashService current = get(id);
        WashService updated = current;
        if (rawName != null) {
            String name = Validators.cleanServiceName(rawName);
            requireUniqueName(name, id);
            updated = updated.withName(name);
        }
        if (durationMinutes != null) {
            checkDuration(durationMinutes);
            updated = updated.withDuration(durationMinutes);
        }
        if (prices != null) {
            checkPrices(prices);
            updated = updated.withPrices(prices);
        }
        if (updated.equals(current)) {
            throw new ValidationException("Nothing to change.");
        }
        services.update(updated);
        return updated;
    }

    /** Hides or shows a service for new bookings. Old bookings are not affected. */
    public WashService setActive(int id, boolean active) {
        WashService current = get(id);
        if (current.active() == active) {
            throw new ValidationException("Service is already " + (active ? "active." : "inactive."));
        }
        WashService updated = current.withActive(active);
        services.update(updated);
        return updated;
    }

    /** Removes a service that has never been booked. Booked services must be deactivated instead. */
    public void delete(int id) {
        get(id);
        if (bookings.existsUsingService(id)) {
            throw new BusinessRuleException("This service has been booked before and cannot be deleted. "
                    + "Deactivate it instead.");
        }
        services.remove(id);
    }

    private void requireUniqueName(String name, int ignoreId) {
        services.filter(s -> s.id() != ignoreId && s.name().equalsIgnoreCase(name)).stream().findFirst()
                .ifPresent(s -> {
                    throw new DuplicateException("A service named " + s.name() + " already exists.", s.id());
                });
    }

    private static void checkDuration(int minutes) {
        if (minutes < app.Config.MIN_DURATION_MINUTES || minutes > app.Config.MAX_DURATION_MINUTES) {
            throw new ValidationException("Duration must be between " + app.Config.MIN_DURATION_MINUTES + " and "
                    + app.Config.MAX_DURATION_MINUTES + " minutes.");
        }
    }

    private static void checkPrices(Map<VehicleType, Long> prices) {
        if (prices == null || prices.isEmpty()) {
            throw new ValidationException("A service needs a price for at least one vehicle type.");
        }
        for (Long p : prices.values()) {
            if (p == null || p <= 0 || p > app.Config.MAX_PRICE) {
                throw new ValidationException("Prices must be between 1 and " + Money.format(app.Config.MAX_PRICE) + ".");
            }
        }
    }
}
