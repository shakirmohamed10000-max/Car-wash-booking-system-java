package service;

import app.Config;
import exception.BusinessRuleException;
import exception.NotFoundException;
import exception.ValidationException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import model.Booking;
import model.BookingItem;
import model.BookingStatus;
import model.Customer;
import model.Vehicle;
import model.VehicleType;
import model.WashService;
import storage.BookingRepository;
import storage.CustomerRepository;
import storage.ServiceRepository;
import storage.VehicleRepository;

/** Business rules for bookings: creation, editing, the status flow and lookups. */
public final class BookingService {

    private final BookingRepository bookings;
    private final CustomerRepository customers;
    private final VehicleRepository vehicles;
    private final ServiceRepository services;
    private final Clock clock;

    public BookingService(BookingRepository bookings, CustomerRepository customers, VehicleRepository vehicles,
                          ServiceRepository services, Clock clock) {
        this.bookings = bookings;
        this.customers = customers;
        this.vehicles = vehicles;
        this.services = services;
        this.clock = clock;
    }

    // ---- create and edit ----

    /**
     * Creates a booking. Everything is checked again here, so a service that was deactivated
     * (or a vehicle that was booked by someone else) while the attendant was choosing is caught.
     */
    public Booking create(int customerId, int vehicleId, List<Integer> serviceIds) {
        Customer customer = customers.find(customerId).filter(Customer::active)
                .orElseThrow(() -> new NotFoundException("No customer with ID " + customerId + "."));
        Vehicle vehicle = vehicles.find(vehicleId).filter(Vehicle::active)
                .orElseThrow(() -> new NotFoundException("No vehicle with ID " + vehicleId + "."));
        if (vehicle.customerId() != customer.id()) {
            throw new BusinessRuleException("That vehicle does not belong to this customer.");
        }
        bookings.findActiveByVehicle(vehicle.id()).ifPresent(b -> {
            throw new BusinessRuleException("This vehicle already has an active booking (" + b.number() + ").");
        });
        List<BookingItem> items = buildItems(serviceIds, vehicle.type());
        long subtotal = items.stream().mapToLong(BookingItem::price).sum();
        int percent = discountPercentFor(customer);
        long discount = Money.percentOf(subtotal, percent);
        return bookings.add(id -> new Booking(id, customer.id(), customer.name(), vehicle.id(), vehicle.plate(),
                vehicle.type(), items, subtotal, percent, discount, subtotal - discount, BookingStatus.BOOKED,
                clock.instant(), null, null, null, null));
    }

    /** Adds one service to a booking that has not started. */
    public Booking addService(int bookingId, int serviceId) {
        Booking b = getById(bookingId);
        requireWaiting(b);
        if (b.items().stream().anyMatch(i -> i.serviceId() == serviceId)) {
            throw new ValidationException("That service is already on the booking.");
        }
        List<BookingItem> items = new ArrayList<>(b.items());
        items.addAll(buildItems(List.of(serviceId), b.vehicleType()));
        return save(applyItems(b, items));
    }

    /** Removes one service from a booking that has not started. At least one must remain. */
    public Booking removeService(int bookingId, int serviceId) {
        Booking b = getById(bookingId);
        requireWaiting(b);
        List<BookingItem> items = new ArrayList<>(b.items());
        if (!items.removeIf(i -> i.serviceId() == serviceId)) {
            throw new NotFoundException("That service is not on the booking.");
        }
        if (items.isEmpty()) {
            throw new BusinessRuleException("A booking needs at least one service. Cancel the booking instead.");
        }
        return save(applyItems(b, items));
    }

    /** Names of selected services that normally cover the same work. Empty when there is no overlap. */
    public List<String> overlapWarnings(List<WashService> selected) {
        Set<String> names = new HashSet<>();
        selected.forEach(s -> names.add(s.name().toLowerCase(Locale.ROOT)));
        List<String> warnings = new ArrayList<>();
        for (List<String> pair : Config.OVERLAPPING_SERVICES) {
            if (names.containsAll(pair)) {
                warnings.add(pair.get(0) + " and " + pair.get(1) + " overlap.");
            }
        }
        return warnings;
    }

    // ---- status flow ----

    public Booking start(int bookingId) {
        return save(getById(bookingId).start(clock.instant()));
    }

    public Booking complete(int bookingId) {
        return save(getById(bookingId).complete(clock.instant()));
    }

    public Booking cancel(int bookingId, String rawReason) {
        String reason = Validators.cleanText(rawReason, "Reason", 3, 120);
        return save(getById(bookingId).cancel(clock.instant(), reason));
    }

    // ---- lookups ----

    /** Accepts "BK-000012", "bk-12" or "12". */
    public Booking get(String reference) {
        return getById(parseNumber(reference));
    }

    public Booking getById(int id) {
        return bookings.find(id).orElseThrow(() -> new NotFoundException("No booking " + Booking.formatNumber(id) + "."));
    }

    public static int parseNumber(String reference) {
        String s = reference == null ? "" : reference.trim().toUpperCase(Locale.ROOT);
        if (s.startsWith("BK-")) {
            s = s.substring(3);
        }
        if (!s.matches("\\d{1,9}") || Integer.parseInt(s) <= 0) {
            throw new ValidationException("Booking number should look like BK-000012 or 12.");
        }
        return Integer.parseInt(s);
    }

    public java.util.Optional<Booking> findActiveForVehicle(int vehicleId) {
        return bookings.findActiveByVehicle(vehicleId);
    }

    /** Newest first. */
    public List<Booking> listAll() {
        return bookings.all().stream().sorted(Comparator.comparing(Booking::createdAt).reversed()
                .thenComparing(Comparator.comparingInt(Booking::id).reversed())).toList();
    }

    /** Waiting and in-progress bookings, oldest first, which is the order they should be served. */
    public List<Booking> listActive() {
        return bookings.filter(b -> b.status().isActive()).stream()
                .sorted(Comparator.comparing(Booking::createdAt).thenComparingInt(Booking::id)).toList();
    }

    public List<Booking> forCustomer(int customerId) {
        return listAll().stream().filter(b -> b.customerId() == customerId).toList();
    }

    public List<Booking> forVehicle(int vehicleId) {
        return listAll().stream().filter(b -> b.vehicleId() == vehicleId).toList();
    }

    /** Matches part of the plate (ignoring spacing) or part of the customer name. Past bookings included. */
    public List<Booking> search(String query) {
        String text = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String key = Validators.plateKey(query);
        if (text.isEmpty()) {
            return List.of();
        }
        return listAll().stream()
                .filter(b -> (!key.isEmpty() && Validators.plateKey(b.plate()).contains(key))
                        || b.customerName().toLowerCase(Locale.ROOT).contains(text))
                .toList();
    }

    // ---- internals ----

    /**
     * Hook for the regular-customer discount. Returns 0 until the discount stage is built;
     * the booking already stores the percentage and amount so no data change is needed later.
     */
    private int discountPercentFor(Customer customer) {
        return 0;
    }

    private Booking applyItems(Booking b, List<BookingItem> items) {
        long subtotal = items.stream().mapToLong(BookingItem::price).sum();
        int percent = b.discountPercent();
        return b.withItems(items, percent, Money.percentOf(subtotal, percent));
    }

    private List<BookingItem> buildItems(List<Integer> serviceIds, VehicleType type) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            throw new ValidationException("Choose at least one service.");
        }
        if (new HashSet<>(serviceIds).size() != serviceIds.size()) {
            throw new ValidationException("A service was chosen more than once.");
        }
        List<BookingItem> items = new ArrayList<>();
        for (int serviceId : serviceIds) {
            WashService s = services.find(serviceId)
                    .orElseThrow(() -> new NotFoundException("No service with ID " + serviceId + "."));
            if (!s.active()) {
                throw new BusinessRuleException(s.name() + " is no longer available.");
            }
            long price = s.priceFor(type).orElseThrow(
                    () -> new BusinessRuleException(s.name() + " is not offered for " + type.label() + "."));
            items.add(new BookingItem(s.id(), s.name(), price));
        }
        return items;
    }

    private static void requireWaiting(Booking b) {
        if (b.status() != BookingStatus.BOOKED) {
            throw new BusinessRuleException("Services can only be changed while the booking is still waiting. "
                    + "Make a new booking for extras.");
        }
    }

    private Booking save(Booking b) {
        bookings.update(b);
        return b;
    }
}
