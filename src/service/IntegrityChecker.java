package service;

import app.AppLog;
import java.util.ArrayList;
import java.util.List;
import storage.BookingRepository;
import storage.CustomerRepository;
import storage.PaymentRepository;
import storage.VehicleRepository;

/** Looks for records that point at something that no longer exists. Runs once at startup. */
public final class IntegrityChecker {

    private IntegrityChecker() {
    }

    /**
     * @return one short summary line per kind of problem. Details (ids only) go to the log.
     */
    public static List<String> check(CustomerRepository customers, VehicleRepository vehicles, BookingRepository bookings) {
        return check(customers, vehicles, bookings, null);
    }

    public static List<String> check(CustomerRepository customers, VehicleRepository vehicles, BookingRepository bookings,
                                     PaymentRepository payments) {
        List<String> summary = new ArrayList<>();

        int orphanVehicles = 0;
        for (var v : vehicles.all()) {
            if (customers.find(v.customerId()).isEmpty()) {
                orphanVehicles++;
                AppLog.warn("Vehicle#" + v.id() + " points at missing Customer#" + v.customerId());
            }
        }
        if (orphanVehicles > 0) {
            summary.add(orphanVehicles + " vehicle(s) belong to a customer who is missing from the data.");
        }

        int orphanBookings = 0;
        for (var b : bookings.all()) {
            boolean missingCustomer = customers.find(b.customerId()).isEmpty();
            boolean missingVehicle = vehicles.find(b.vehicleId()).isEmpty();
            if (missingCustomer || missingVehicle) {
                orphanBookings++;
                AppLog.warn("Booking#" + b.id() + " points at missing records (customer missing: "
                        + missingCustomer + ", vehicle missing: " + missingVehicle + ")");
            }
        }
        if (orphanBookings > 0) {
            summary.add(orphanBookings + " booking(s) refer to a customer or vehicle that is missing from the data.");
        }
        if (payments != null) {
            int orphanPayments = 0;
            for (var p : payments.all()) {
                if (bookings.find(p.bookingId()).isEmpty()) {
                    orphanPayments++;
                    AppLog.warn("Payment#" + p.id() + " points at missing Booking#" + p.bookingId());
                }
            }
            if (orphanPayments > 0) {
                summary.add(orphanPayments + " payment ledger entry/entries refer to a booking that is missing from the data.");
            }
        }
        return summary;
    }
}
