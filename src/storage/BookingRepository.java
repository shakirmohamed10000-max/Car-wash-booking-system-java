package storage;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import model.Booking;
import model.BookingItem;
import model.BookingStatus;
import model.VehicleType;

/**
 * Stores bookings in bookings.txt. The services of a booking are kept inside the booking's
 * own line (serviceId~encodedName~price, joined by ';'), so a booking and its items are always
 * saved together in one atomic write.
 */
public final class BookingRepository extends Repository<Booking> {

    public BookingRepository(FileStore store) {
        super(store, "bookings.txt",
                "id,customerId,customerName,vehicleId,plate,type,items,subtotal,discountPercent,discountAmount,"
                        + "total,status,createdAt,startedAt,completedAt,cancelledAt,cancelReason,scheduledAt");
    }

    @Override
    protected int idOf(Booking b) {
        return b.id();
    }

    @Override
    protected List<String> toFields(Booking b) {
        List<String> f = new ArrayList<>();
        f.add(String.valueOf(b.id()));
        f.add(String.valueOf(b.customerId()));
        f.add(b.customerName());
        f.add(String.valueOf(b.vehicleId()));
        f.add(b.plate());
        f.add(b.vehicleType().name());
        f.add(encodeItems(b.items()));
        f.add(String.valueOf(b.subtotal()));
        f.add(String.valueOf(b.discountPercent()));
        f.add(String.valueOf(b.discountAmount()));
        f.add(String.valueOf(b.total()));
        f.add(b.status().name());
        f.add(b.createdAt().toString());
        f.add(timeOrEmpty(b.startedAt()));
        f.add(timeOrEmpty(b.completedAt()));
        f.add(timeOrEmpty(b.cancelledAt()));
        f.add(b.cancelReason() == null ? "" : b.cancelReason());
        f.add(timeOrEmpty(b.scheduledAt()));
        return f;
    }

    @Override
    protected Booking fromFields(List<String> f) {
        if (f.size() != 17 && f.size() != 18) {
            throw new IllegalArgumentException("Wrong number of fields.");
        }
        String reason = f.get(16);
        return new Booking(
                Integer.parseInt(f.get(0)),
                Integer.parseInt(f.get(1)),
                f.get(2),
                Integer.parseInt(f.get(3)),
                f.get(4),
                VehicleType.parse(f.get(5)),
                decodeItems(f.get(6)),
                Long.parseLong(f.get(7)),
                Integer.parseInt(f.get(8)),
                Long.parseLong(f.get(9)),
                Long.parseLong(f.get(10)),
                BookingStatus.valueOf(f.get(11)),
                Instant.parse(f.get(12)),
                parseTime(f.get(13)),
                parseTime(f.get(14)),
                parseTime(f.get(15)),
                reason.isEmpty() ? null : reason,
                f.size() == 18 ? parseTime(f.get(17)) : null);
    }

    // ---- queries used by the services ----

    public Optional<Booking> findActiveByVehicle(int vehicleId) {
        return all().stream().filter(b -> b.vehicleId() == vehicleId && b.status().isActive()).findFirst();
    }

    public boolean existsForVehicle(int vehicleId) {
        return exists(b -> b.vehicleId() == vehicleId);
    }

    public boolean existsForCustomer(int customerId) {
        return exists(b -> b.customerId() == customerId);
    }

    public boolean existsActiveForCustomer(int customerId) {
        return exists(b -> b.customerId() == customerId && b.status().isActive());
    }

    public boolean existsUsingService(int serviceId) {
        return exists(b -> b.items().stream().anyMatch(i -> i.serviceId() == serviceId));
    }

    // ---- item encoding ----

    private static String encodeItems(List<BookingItem> items) {
        return items.stream()
                .map(i -> i.serviceId() + "~" + URLEncoder.encode(i.serviceName(), StandardCharsets.UTF_8) + "~"
                        + i.price() + "~" + i.durationMinutes())
                .collect(Collectors.joining(";"));
    }

    private static List<BookingItem> decodeItems(String text) {
        List<BookingItem> items = new ArrayList<>();
        for (String part : text.split(";")) {
            if (part.isEmpty()) {
                continue;
            }
            String[] p = part.split("~", -1);
            if (p.length != 3 && p.length != 4) {
                throw new IllegalArgumentException("Bad item.");
            }
            items.add(new BookingItem(Integer.parseInt(p[0]), URLDecoder.decode(p[1], StandardCharsets.UTF_8),
                    Long.parseLong(p[2]), p.length == 4 ? Integer.parseInt(p[3]) : 0));
        }
        return items;
    }

    private static String timeOrEmpty(Instant t) {
        return t == null ? "" : t.toString();
    }

    private static Instant parseTime(String s) {
        return s.isEmpty() ? null : Instant.parse(s);
    }
}
