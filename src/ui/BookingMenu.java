package ui;

import app.AppLog;
import exception.StorageException;
import exception.BusinessRuleException;
import exception.ValidationException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import model.Booking;
import model.BookingItem;
import model.Customer;
import model.Vehicle;
import model.WashService;
import service.BookingService;
import service.BookingService.QueueEntry;
import service.Money;
import service.ServiceCatalog;
import service.PaymentService;
import service.Validators;

/** Booking menu: create, follow the status flow, edit services, cancel, look up and print. */
public final class BookingMenu extends Menu {

    private final BookingService bookings;
    private final ServiceCatalog catalog;
    private final Views views;
    private final Pickers pickers;
    private final PaymentService payments;

    public BookingMenu(InputReader in, PrintStream out, BookingService bookings, ServiceCatalog catalog, Views views,
                       Pickers pickers, PaymentService payments) {
        super(in, out);
        this.bookings = bookings;
        this.catalog = catalog;
        this.views = views;
        this.pickers = pickers;
        this.payments = payments;
    }

    public void run() {
        loop("Bookings", "Back to main menu", List.of(
                new Item("New booking", this::create),
                new Item("Schedule appointment", this::createAppointment),
                new Item("Queue and estimated waits", this::queue),
                new Item("Who is next?", this::whoIsNext),
                new Item("View appointments", this::appointments),
                new Item("Reschedule appointment", this::reschedule),
                new Item("Waiting and in-progress bookings", () -> views.bookingList(bookings.listActive())),
                new Item("All bookings", () -> views.bookingList(bookings.listAll())),
                new Item("View or reprint a booking", () -> {
                    begin("View booking");
                    ReceiptPrinter.print(out, pickers.pickBooking());
                }),
                new Item("Start wash", this::start),
                new Item("Complete wash", this::complete),
                new Item("Add or remove services", this::editServices),
                new Item("Cancel booking", this::cancel),
                new Item("Find bookings by plate or customer name", () -> {
                    begin("Find bookings");
                    views.bookingList(bookings.search(in.askNonBlank("Plate or customer name")));
                })));
    }

    private void create() { create(false); }

    private void createAppointment() { create(true); }

    private void create(boolean appointment) {
        begin(appointment ? "Schedule appointment" : "New booking");
        Customer customer = pickers.pickCustomer(true);
        Vehicle vehicle = pickers.pickCustomerVehicle(customer);
        bookings.findActiveForVehicle(vehicle.id()).ifPresent(b -> {
            throw new BusinessRuleException(vehicle.plate() + " already has an active booking (" + b.number() + ").");
        });
        List<WashService> offered = catalog.offeredFor(vehicle.type());
        if (offered.isEmpty()) {
            throw new BusinessRuleException("No active service is offered for " + vehicle.type().label()
                    + ". Add or activate a service first.");
        }
        out.println();
        out.println("Services for " + vehicle.plate() + " (" + vehicle.type().label() + "):");
        views.offered(offered, vehicle.type());
        List<WashService> chosen = in.askValid("Service numbers separated by commas, for example 1,3",
                text -> parseSelection(text, offered));
        java.time.Instant scheduledAt = appointment ? askAppointmentTime() : null;

        List<String> overlaps = bookings.overlapWarnings(chosen);
        if (!overlaps.isEmpty()) {
            overlaps.forEach(w -> out.println("Note: " + w));
            if (!in.askYesNo("Continue with these services")) {
                throw new CancelledException();
            }
        }

        long total = 0;
        out.println();
        out.println("Customer: " + customer.name());
        out.println("Vehicle:  " + vehicle.plate() + " (" + vehicle.type().label() + ")");
        for (WashService s : chosen) {
            long price = s.priceFor(vehicle.type()).getAsLong();
            total += price;
            out.printf("  %-28s %12s%n", s.name(), Money.format(price));
        }
        int percent = bookings.loyaltyPercentFor(customer.id());
        if (percent > 0) {
            long discount = Money.percentOf(total, percent);
            out.printf("  %-28s %12s%n", "Subtotal", Money.format(total));
            out.printf("  %-28s %12s%n", "Regular customer " + percent + "% off", "-" + Money.format(discount));
            total -= discount;
        }
        out.printf("  %-28s %12s%n", "Total", Money.format(total));
        if (!in.askYesNo("Confirm booking")) {
            throw new CancelledException();
        }

        List<Integer> ids = new ArrayList<>();
        chosen.forEach(s -> ids.add(s.id()));
        Booking b = bookings.create(customer.id(), vehicle.id(), ids, scheduledAt);
        out.println("Booking saved: " + b.number() + ".");
        ReceiptPrinter.print(out, b);
    }

    private java.time.Instant askAppointmentTime() {
        return in.askValid("Appointment date and time (YYYY-MM-DD HH:MM)", Validators::parseAppointmentTime);
    }

    private void queue() {
        begin("Queue and estimated waits");
        views.queue(bookings.queue());
        printNextUp();
    }

    private void whoIsNext() {
        out.println();
        out.println("Who is next");
        printNowWashing();
        printNextUp();
    }

    private void printNowWashing() {
        List<Booking> washing = bookings.inProgress();
        if (washing.isEmpty()) {
            out.println("Now washing: nobody");
            return;
        }
        for (Booking b : washing) {
            out.println("Now washing: " + b.plate() + " (" + b.number() + ")");
        }
    }

    private void printNextUp() {
        Optional<QueueEntry> next = bookings.nextUp();
        if (next.isEmpty()) {
            out.println("Next up:     nobody is waiting");
            return;
        }
        Booking b = next.get().booking();
        out.println("Next up:     " + b.plate() + " (" + b.vehicleType().label() + ") - " + b.number());
        out.println("Customer:    " + b.customerName());
        StringBuilder services = new StringBuilder();
        for (BookingItem item : b.items()) {
            if (services.length() > 0) {
                services.append(", ");
            }
            services.append(item.serviceName());
        }
        out.println("Services:    " + services);
        if (b.scheduledAt() != null) {
            out.println("Appointment: " + app.Config.formatTime(b.scheduledAt()));
        }
        out.println("Est. wait:   ~" + next.get().estimatedWaitMinutes() + " min");
    }

    private void appointments() {
        begin("Appointments");
        views.appointmentList(bookings.listAll().stream().filter(b -> b.scheduledAt() != null).toList());
    }

    private void reschedule() {
        begin("Reschedule appointment");
        Booking b = pickers.pickBooking();
        if (b.scheduledAt() == null) throw new BusinessRuleException("That booking is a walk-in, not a scheduled appointment.");
        out.println("Current appointment: " + app.Config.formatTime(b.scheduledAt()));
        Booking updated = bookings.reschedule(b.id(), askAppointmentTime());
        out.println("Appointment updated: " + app.Config.formatTime(updated.scheduledAt()) + ".");
    }

    private static List<WashService> parseSelection(String text, List<WashService> offered) {
        Set<Integer> picks = new LinkedHashSet<>();
        for (String part : text.split("[,\\s]+")) {
            if (part.isEmpty()) {
                continue;
            }
            if (!part.matches("\\d{1,3}") || Integer.parseInt(part) < 1 || Integer.parseInt(part) > offered.size()) {
                throw new ValidationException("Use numbers from 1 to " + offered.size() + ", separated by commas.");
            }
            if (!picks.add(Integer.parseInt(part))) {
                throw new ValidationException("Each service can only be chosen once.");
            }
        }
        if (picks.isEmpty()) {
            throw new ValidationException("Choose at least one service.");
        }
        List<WashService> result = new ArrayList<>();
        picks.forEach(n -> result.add(offered.get(n - 1)));
        return result;
    }

    private void start() {
        begin("Start wash");
        Booking b = pickers.pickBooking();
        if (!in.askYesNo("Start washing " + b.plate() + " (" + b.number() + ")")) {
            out.println("No change made.");
            return;
        }
        bookings.start(b.id());
        out.println("Wash started for " + b.plate() + ".");
    }

    private void complete() {
        begin("Complete wash");
        Booking b = pickers.pickBooking();
        if (!in.askYesNo("Mark " + b.plate() + " (" + b.number() + ") as completed")) {
            out.println("No change made.");
            return;
        }
        Booking done = bookings.complete(b.id());
        out.println("Wash completed for " + done.plate() + ". Total " + Money.format(done.total()) + ".");
    }

    private void editServices() {
        begin("Add or remove services");
        Booking b = pickers.pickBooking();
        if (b.status() != model.BookingStatus.BOOKED) {
            throw new BusinessRuleException("Services can only be changed while the booking is still waiting. "
                    + "Make a new booking for extras.");
        }
        ReceiptPrinter.print(out, b);
        int action = in.askInt("1 = add a service, 2 = remove a service", 1, 2);
        Booking updated;
        if (action == 1) {
            List<WashService> available = catalog.offeredFor(b.vehicleType()).stream()
                    .filter(s -> b.items().stream().noneMatch(i -> i.serviceId() == s.id())).toList();
            if (available.isEmpty()) {
                throw new BusinessRuleException("There are no more services to add for this vehicle.");
            }
            views.offered(available, b.vehicleType());
            int n = in.askInt("Service number", 1, available.size());
            updated = bookings.addService(b.id(), available.get(n - 1).id());
        } else {
            for (int i = 0; i < b.items().size(); i++) {
                BookingItem item = b.items().get(i);
                out.printf("  %d. %s  %s%n", i + 1, item.serviceName(), Money.format(item.price()));
            }
            int n = in.askInt("Service number to remove", 1, b.items().size());
            updated = bookings.removeService(b.id(), b.items().get(n - 1).serviceId());
        }
        out.println("Booking updated.");
        ReceiptPrinter.print(out, updated);
    }

    private void cancel() {
        begin("Cancel booking");
        Booking b = pickers.pickBooking();
        if (!b.status().isActive()) {
            throw new BusinessRuleException("Booking " + b.number() + " is already " + b.status().label().toLowerCase()
                    + " and cannot be cancelled.");
        }
        String reason = in.askValid("Reason for cancelling", text -> Validators.cleanText(text, "Reason", 3, 120));
        if (!in.confirmYes("Cancel " + b.number() + " for " + b.plate() + "?")) {
            out.println("Booking was not cancelled.");
            return;
        }
        Booking cancelled = bookings.cancel(b.id(), reason);
        model.Payment refund = null;
        boolean refundWriteFailed = false;
        if (payments != null) {
            try {
                refund = payments.refundOnCancellation(b.id());
            } catch (StorageException e) {
                refundWriteFailed = true;
                AppLog.error("Cancellation saved but refund ledger write failed for Booking#" + b.id(), e);
            }
        }
        out.println("Booking cancelled.");
        ReceiptPrinter.print(out, cancelled);
        if (refund != null) out.println("Refund of " + Money.format(refund.amount()) + " recorded as "
                + refund.receiptNumber() + ". Issue the refund to the customer manually.");
        if (refundWriteFailed) out.println("The cancellation was saved, but its refund entry could not be saved. It will be retried at the next startup.");
    }
}
