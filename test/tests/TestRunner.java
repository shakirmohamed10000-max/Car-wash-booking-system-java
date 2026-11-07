package tests;

import app.AppLog;
import exception.AppException;
import exception.BusinessRuleException;
import exception.DuplicateException;
import exception.NotFoundException;
import exception.ValidationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import model.Booking;
import model.BookingStatus;
import model.Customer;
import model.Vehicle;
import model.VehicleType;
import model.WashService;
import service.BookingService;
import service.CustomerService;
import service.DeleteOutcome;
import service.IntegrityChecker;
import service.Money;
import service.ServiceCatalog;
import service.Validators;
import service.VehicleService;
import storage.BookingRepository;
import storage.CustomerRepository;
import storage.FileStore;
import storage.ServiceRepository;
import storage.VehicleRepository;

/**
 * Plain-Java test runner (no libraries needed).
 * Run: java -cp out tests.TestRunner   (exit code 1 if anything fails)
 */
public final class TestRunner {

    private static int passed;
    private static int failed;

    /** Everything wired against a throw-away data folder. */
    private static final class Env implements AutoCloseable {
        final Path dir;
        final FileStore store;
        final Clock clock = Clock.fixed(Instant.parse("2026-11-07T11:00:00Z"), ZoneOffset.UTC);
        CustomerRepository customerRepo;
        VehicleRepository vehicleRepo;
        ServiceRepository serviceRepo;
        BookingRepository bookingRepo;
        ServiceCatalog catalog;
        CustomerService customers;
        VehicleService vehicles;
        BookingService bookings;

        Env(Path dir) {
            this.dir = dir;
            this.store = new FileStore(dir);
            load();
        }

        void load() {
            customerRepo = new CustomerRepository(store);
            vehicleRepo = new VehicleRepository(store);
            serviceRepo = new ServiceRepository(store);
            bookingRepo = new BookingRepository(store);
            customerRepo.load();
            vehicleRepo.load();
            serviceRepo.load();
            bookingRepo.load();
            catalog = new ServiceCatalog(serviceRepo, bookingRepo);
            catalog.seedDefaultsIfFirstRun();
            customers = new CustomerService(customerRepo, vehicleRepo, bookingRepo, clock);
            vehicles = new VehicleService(vehicleRepo, customerRepo, bookingRepo, clock);
            bookings = new BookingService(bookingRepo, customerRepo, vehicleRepo, serviceRepo, clock);
        }

        @Override
        public void close() {
            store.close();
        }
    }

    public static void main(String[] args) throws Exception {
        AppLog.mask("x"); // touch the logger class; logging stays off in tests

        run("name cleaning", TestRunner::nameCleaning);
        run("phone normalising and masking", TestRunner::phones);
        run("plate normalising and type warning", TestRunner::plates);
        run("price and duration parsing", TestRunner::numbers);
        run("money formatting and rounding", TestRunner::money);
        run("line escaping round trip", TestRunner::escaping);
        run("customer CRUD", TestRunner::customerCrud);
        run("customer delete rules", TestRunner::customerDeleteRules);
        run("vehicle CRUD and rules", TestRunner::vehicleRules);
        run("service catalogue rules", TestRunner::serviceRules);
        run("booking creation rules", TestRunner::bookingCreation);
        run("booking status transitions", TestRunner::bookingTransitions);
        run("booking edit and cancel", TestRunner::bookingEdit);
        run("snapshots survive later edits", TestRunner::snapshots);
        run("data survives a restart", TestRunner::persistence);
        run("ids are never reused", TestRunner::idsNotReused);
        run("corrupt line is backed up and skipped", TestRunner::corruptLine);
        run("second instance is locked out", TestRunner::locking);
        run("integrity checker finds orphans", TestRunner::orphans);
        run("log masking", TestRunner::logMasking);

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed.");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ---- tests ----

    private static void nameCleaning() {
        eq("Shakir Mohamed", Validators.cleanName("  shakir   MOHAMED "));
        eq("O'Brien", Validators.cleanName("o'brien"));
        eq("Anne-Marie Wanjiru", Validators.cleanName("ANNE-MARIE WANJIRU"));
        eq("McDonald", Validators.cleanName("McDonald"));
        eq("محمد علي", Validators.cleanName("محمد علي"));
        throwsA(ValidationException.class, () -> Validators.cleanName(""));
        throwsA(ValidationException.class, () -> Validators.cleanName("A"));
        throwsA(ValidationException.class, () -> Validators.cleanName("John|Doe"));
        throwsA(ValidationException.class, () -> Validators.cleanName("John123"));
        throwsA(ValidationException.class, () -> Validators.cleanName("x".repeat(61)));
    }

    private static void phones() {
        for (String in : new String[] {"0712345678", "+254712345678", "254712345678", "0712 345 678", "0712-345-678",
                "(0712) 345678"}) {
            eq("+254712345678", Validators.cleanPhone(in));
        }
        eq("+254112345678", Validators.cleanPhone("0112345678"));
        for (String bad : new String[] {"", "abc", "0812345678", "071234567", "07123456789", "+255712345678", "712345678"}) {
            throwsA(ValidationException.class, () -> Validators.cleanPhone(bad));
        }
        eq("0712 *** 678", Validators.maskPhone("+254712345678"));
        eq("0712 345 678", Validators.displayPhone("+254712345678"));
    }

    private static void plates() {
        eq("KAA 123A", Validators.cleanPlate("kaa123a"));
        eq("KAA 123A", Validators.cleanPlate(" KAA  123A "));
        eq("KMAA 123A", Validators.cleanPlate("kmaa123a"));
        eq("GK 123A", Validators.cleanPlate("gk123a"));
        throwsA(ValidationException.class, () -> Validators.cleanPlate("ABC 123A"));
        throwsA(ValidationException.class, () -> Validators.cleanPlate("KAA 12A"));
        throwsA(ValidationException.class, () -> Validators.cleanPlate(""));
        check(Validators.plateTypeWarning("KMAA 123A", VehicleType.CAR).isPresent());
        check(Validators.plateTypeWarning("KMAA 123A", VehicleType.BIKE).isEmpty());
        check(Validators.plateTypeWarning("KAA 123A", VehicleType.BIKE).isPresent());
        check(Validators.plateTypeWarning("KMD 123A", VehicleType.CAR).isEmpty());
    }

    private static void numbers() {
        eq(300L, Validators.parsePrice("300"));
        eq(1200L, Validators.parsePrice("KSh 1,200"));
        for (String bad : new String[] {"0", "-5", "abc", "100001", "1.5", ""}) {
            throwsA(ValidationException.class, () -> Validators.parsePrice(bad));
        }
        eq(30, Validators.parseDuration("30"));
        throwsA(ValidationException.class, () -> Validators.parseDuration("4"));
        throwsA(ValidationException.class, () -> Validators.parseDuration("9999"));
    }

    private static void money() {
        eq("KSh 1,200", Money.format(1200));
        eq("KSh 0", Money.format(0));
        eq(35L, Money.percentOf(700, 5));
        eq(3L, Money.percentOf(25, 10));   // 2.5 rounds up
        eq(0L, Money.percentOf(500, 0));
    }

    private static void escaping() {
        String nasty = "a|b\\c\nd\re|";
        List<String> fields = List.of("1", nasty, "", "x");
        String line = FileStore.join(fields);
        check(!line.contains("\n") && !line.contains("\r"));
        eq(fields, FileStore.split(line));
        throwsA(IllegalArgumentException.class, () -> FileStore.split("abc\\"));
        throwsA(IllegalArgumentException.class, () -> FileStore.split("a\\qb"));
    }

    private static void customerCrud() throws IOException {
        try (Env e = env()) {
            Customer a = e.customers.register("amina hassan", "0712345678");
            eq(1, a.id());
            eq("Amina Hassan", a.name());
            eq("+254712345678", a.phone());
            throwsA(DuplicateException.class, () -> e.customers.register("Someone Else", "+254 712 345 678"));
            Customer b = e.customers.register("Brian Otieno", "0722000111");
            eq(2, b.id());
            eq(2, e.customers.list().size());
            eq(1, e.customers.search("amin").size());
            eq(1, e.customers.search("0722").size());
            eq(0, e.customers.search("zzz").size());
            eq(0, e.customers.search("12").size());   // fewer than 3 digits never matches phones

            Customer renamed = e.customers.update(a.id(), "Amina H Hassan", null);
            eq("Amina H Hassan", renamed.name());
            eq("+254712345678", renamed.phone());
            throwsA(DuplicateException.class, () -> e.customers.update(a.id(), null, "0722000111"));
            throwsA(ValidationException.class, () -> e.customers.update(a.id(), null, null));
            throwsA(ValidationException.class, () -> e.customers.update(a.id(), "Amina H Hassan", null));
            throwsA(NotFoundException.class, () -> e.customers.get(99));
            eq(1, e.customers.sameName("brian otieno").size());
        }
    }

    private static void customerDeleteRules() throws IOException {
        try (Env e = env()) {
            Customer c = e.customers.register("Plain Customer", "0711111111");
            eq(DeleteOutcome.DELETED, e.customers.delete(c.id()));
            throwsA(NotFoundException.class, () -> e.customers.get(c.id()));

            Customer d = e.customers.register("Has Vehicle", "0722222222");
            Vehicle v = e.vehicles.add("KAA 111A", VehicleType.CAR, d.id());
            throwsA(BusinessRuleException.class, () -> e.customers.delete(d.id()));

            Booking b = e.bookings.create(d.id(), v.id(), List.of(1));
            e.bookings.start(b.id());
            throwsA(BusinessRuleException.class, () -> e.vehicles.delete(v.id()));
            e.bookings.complete(b.id());
            eq(DeleteOutcome.ARCHIVED, e.vehicles.delete(v.id()));
            eq(DeleteOutcome.ARCHIVED, e.customers.delete(d.id()));
            eq(0, e.customers.list().size());

            // the archived number can be restored with its history
            DuplicateException dup = expect(DuplicateException.class, () -> e.customers.register("Has Vehicle", "0722222222"));
            Customer restored = e.customers.restore(dup.existingId());
            eq(d.id(), restored.id());
            eq(1, e.bookings.forCustomer(restored.id()).size());
        }
    }

    private static void vehicleRules() throws IOException {
        try (Env e = env()) {
            Customer a = e.customers.register("Amina Hassan", "0712345678");
            Customer b = e.customers.register("Brian Otieno", "0722000111");
            throwsA(NotFoundException.class, () -> e.vehicles.add("KAA 123A", VehicleType.CAR, 99));
            Vehicle v = e.vehicles.add("kaa123a", VehicleType.CAR, a.id());
            eq("KAA 123A", v.plate());
            throwsA(DuplicateException.class, () -> e.vehicles.add("KAA 123A", VehicleType.SUV, b.id()));
            throwsA(ValidationException.class, () -> e.vehicles.add("BAD", VehicleType.CAR, a.id()));

            eq(1, e.vehicles.search("kaa123").size());
            eq(1, e.vehicles.search("123a").size());
            eq(0, e.vehicles.search("zzz").size());
            eq(1, e.vehicles.byCustomer(a.id()).size());
            eq(0, e.vehicles.byCustomer(b.id()).size());

            Vehicle moved = e.vehicles.update(v.id(), null, VehicleType.SUV, b.id());
            eq(b.id(), moved.customerId());
            eq(VehicleType.SUV, moved.type());
            Vehicle other = e.vehicles.add("KBB 222B", VehicleType.CAR, a.id());
            throwsA(DuplicateException.class, () -> e.vehicles.update(other.id(), "KAA 123A", null, null));
            throwsA(ValidationException.class, () -> e.vehicles.update(other.id(), null, null, null));

            // type and owner are locked while a booking is active; plate is not
            Booking bk = e.bookings.create(b.id(), v.id(), List.of(1));
            throwsA(BusinessRuleException.class, () -> e.vehicles.update(v.id(), null, VehicleType.CAR, null));
            throwsA(BusinessRuleException.class, () -> e.vehicles.update(v.id(), null, null, a.id()));
            eq("KCC 333C", e.vehicles.update(v.id(), "KCC 333C", null, null).plate());
            check(e.vehicles.hasHistory(v.id()));
            check(bk.status() == BookingStatus.BOOKED);

            // a hard-deleted plate can be reused; an archived one is revived
            eq(DeleteOutcome.DELETED, e.vehicles.delete(other.id()));
            Vehicle again = e.vehicles.add("KBB 222B", VehicleType.CAR, b.id());
            check(again.id() != other.id());
        }
    }

    private static void serviceRules() throws IOException {
        try (Env e = env()) {
            eq(4, e.catalog.listAll().size());
            WashService basic = e.catalog.get(1);
            eq(300L, basic.priceFor(VehicleType.CAR).getAsLong());
            check(e.catalog.get(3).priceFor(VehicleType.BIKE).isEmpty());   // no interior on a bike
            eq(2, e.catalog.offeredFor(VehicleType.BIKE).size());
            eq(4, e.catalog.offeredFor(VehicleType.CAR).size());

            Map<VehicleType, Long> prices = new EnumMap<>(VehicleType.class);
            prices.put(VehicleType.CAR, 250L);
            throwsA(DuplicateException.class, () -> e.catalog.create("basic wash", 20, prices));
            throwsA(ValidationException.class, () -> e.catalog.create("Polish", 20, new EnumMap<>(VehicleType.class)));
            throwsA(ValidationException.class, () -> e.catalog.create("Polish", 2, prices));
            throwsA(ValidationException.class, () -> e.catalog.create("Bad|Name", 20, prices));
            WashService polish = e.catalog.create("Polish", 20, prices);
            eq(5, polish.id());

            e.catalog.setActive(polish.id(), false);
            eq(4, e.catalog.offeredFor(VehicleType.CAR).size());
            throwsA(ValidationException.class, () -> e.catalog.setActive(polish.id(), false));

            Customer c = e.customers.register("Amina Hassan", "0712345678");
            Vehicle v = e.vehicles.add("KAA 123A", VehicleType.CAR, c.id());
            e.bookings.create(c.id(), v.id(), List.of(1));
            throwsA(BusinessRuleException.class, () -> e.catalog.delete(1));
            e.catalog.delete(polish.id());   // never booked, so it can go
            throwsA(NotFoundException.class, () -> e.catalog.get(polish.id()));
        }
    }

    private static void bookingCreation() throws IOException {
        try (Env e = env()) {
            Customer a = e.customers.register("Amina Hassan", "0712345678");
            Customer b = e.customers.register("Brian Otieno", "0722000111");
            Vehicle car = e.vehicles.add("KAA 123A", VehicleType.CAR, a.id());
            Vehicle bike = e.vehicles.add("KMAA 123A", VehicleType.BIKE, a.id());

            Booking bk = e.bookings.create(a.id(), car.id(), List.of(1, 4));
            eq("BK-000001", bk.number());
            eq(700L, bk.subtotal());          // 300 + 400
            eq(700L, bk.total());
            eq(0L, bk.discountAmount());
            eq(BookingStatus.BOOKED, bk.status());
            eq("Amina Hassan", bk.customerName());

            throwsA(BusinessRuleException.class, () -> e.bookings.create(a.id(), car.id(), List.of(2)));   // already active
            throwsA(BusinessRuleException.class, () -> e.bookings.create(b.id(), bike.id(), List.of(1))); // not their vehicle
            throwsA(BusinessRuleException.class, () -> e.bookings.create(a.id(), bike.id(), List.of(3))); // not offered for bike
            throwsA(ValidationException.class, () -> e.bookings.create(a.id(), bike.id(), List.of()));
            throwsA(ValidationException.class, () -> e.bookings.create(a.id(), bike.id(), List.of(1, 1)));
            throwsA(NotFoundException.class, () -> e.bookings.create(a.id(), bike.id(), List.of(99)));
            throwsA(NotFoundException.class, () -> e.bookings.create(99, bike.id(), List.of(1)));

            e.catalog.setActive(2, false);   // deactivated while the attendant was choosing
            throwsA(BusinessRuleException.class, () -> e.bookings.create(a.id(), bike.id(), List.of(2)));
            Booking bikeBooking = e.bookings.create(a.id(), bike.id(), List.of(1));
            eq(100L, bikeBooking.total());

            eq(2, e.bookings.listActive().size());
            eq("BK-000001", e.bookings.listActive().get(0).number());   // oldest first
            eq(1, e.bookings.search("kaa").size());
            eq(1, e.bookings.search("KMAA 123").size());
            eq(2, e.bookings.search("amina").size());
            eq(2, e.bookings.forCustomer(a.id()).size());
            eq(1, e.bookings.get("bk-1").id());
            eq(1, e.bookings.get("1").id());
            throwsA(ValidationException.class, () -> e.bookings.get("abc"));
            throwsA(ValidationException.class, () -> e.bookings.get("0"));
            throwsA(NotFoundException.class, () -> e.bookings.get("BK-000099"));

            List<WashService> picked = List.of(e.catalog.get(1), e.catalog.get(3));
            eq(1, e.bookings.overlapWarnings(picked).size());
            eq(0, e.bookings.overlapWarnings(List.of(e.catalog.get(1), e.catalog.get(4))).size());
        }
    }

    private static void bookingTransitions() throws IOException {
        try (Env e = env()) {
            Customer a = e.customers.register("Amina Hassan", "0712345678");
            Vehicle v = e.vehicles.add("KAA 123A", VehicleType.CAR, a.id());
            Booking bk = e.bookings.create(a.id(), v.id(), List.of(1));

            throwsA(BusinessRuleException.class, () -> e.bookings.complete(bk.id()));   // must start first
            eq(BookingStatus.IN_PROGRESS, e.bookings.start(bk.id()).status());
            throwsA(BusinessRuleException.class, () -> e.bookings.start(bk.id()));
            Booking done = e.bookings.complete(bk.id());
            eq(BookingStatus.COMPLETED, done.status());
            check(done.startedAt() != null && done.completedAt() != null);
            throwsA(BusinessRuleException.class, () -> e.bookings.cancel(bk.id(), "changed my mind"));
            throwsA(BusinessRuleException.class, () -> e.bookings.start(bk.id()));

            // the same vehicle can be booked again after completion
            Booking second = e.bookings.create(a.id(), v.id(), List.of(1));
            Booking cancelled = e.bookings.cancel(second.id(), "customer left");
            eq(BookingStatus.CANCELLED, cancelled.status());
            eq("customer left", cancelled.cancelReason());
            throwsA(BusinessRuleException.class, () -> e.bookings.cancel(second.id(), "again"));
            throwsA(BusinessRuleException.class, () -> e.bookings.complete(second.id()));
            eq(0, e.bookings.listActive().size());
        }
    }

    private static void bookingEdit() throws IOException {
        try (Env e = env()) {
            Customer a = e.customers.register("Amina Hassan", "0712345678");
            Vehicle v = e.vehicles.add("KAA 123A", VehicleType.CAR, a.id());
            Booking bk = e.bookings.create(a.id(), v.id(), List.of(1));

            Booking plus = e.bookings.addService(bk.id(), 4);
            eq(700L, plus.total());
            throwsA(ValidationException.class, () -> e.bookings.addService(bk.id(), 4));
            Booking minus = e.bookings.removeService(bk.id(), 1);
            eq(400L, minus.total());
            eq(1, minus.items().size());
            throwsA(BusinessRuleException.class, () -> e.bookings.removeService(bk.id(), 4));   // last one
            throwsA(NotFoundException.class, () -> e.bookings.removeService(bk.id(), 2));
            throwsA(ValidationException.class, () -> e.bookings.cancel(bk.id(), "x"));          // reason too short

            e.bookings.start(bk.id());
            throwsA(BusinessRuleException.class, () -> e.bookings.addService(bk.id(), 1));
            throwsA(BusinessRuleException.class, () -> e.bookings.removeService(bk.id(), 4));
        }
    }

    private static void snapshots() throws IOException {
        try (Env e = env()) {
            Customer a = e.customers.register("Amina Hassan", "0712345678");
            Vehicle v = e.vehicles.add("KAA 123A", VehicleType.CAR, a.id());
            Booking bk = e.bookings.create(a.id(), v.id(), List.of(1));

            e.customers.update(a.id(), "Amina Renamed", null);
            Map<VehicleType, Long> newPrices = new EnumMap<>(VehicleType.class);
            newPrices.put(VehicleType.CAR, 999L);
            e.catalog.update(1, "Basic Wash Plus", null, newPrices);
            e.vehicles.update(v.id(), "KBB 222B", null, null);

            Booking again = e.bookings.getById(bk.id());
            eq("Amina Hassan", again.customerName());
            eq("KAA 123A", again.plate());
            eq("Basic Wash", again.items().get(0).serviceName());
            eq(300L, again.total());
        }
    }

    private static void persistence() throws IOException {
        Path dir = Files.createTempDirectory("carwash-test");
        try {
            try (Env first = new Env(dir)) {
                Customer a = first.customers.register("Amina Hassan", "0712345678");
                Vehicle v = first.vehicles.add("KAA 123A", VehicleType.CAR, a.id());
                Booking bk = first.bookings.create(a.id(), v.id(), List.of(1, 4));
                first.bookings.start(bk.id());
                first.catalog.setActive(2, false);
            }
            try (Env second = new Env(dir)) {   // simulates closing the terminal and running java again
                eq(1, second.customers.list().size());
                eq("Amina Hassan", second.customers.get(1).name());
                eq(1, second.vehicles.list().size());
                eq("KAA 123A", second.vehicles.get(1).plate());
                eq(4, second.catalog.listAll().size());   // seeded once, not again
                check(!second.catalog.get(2).active());
                Booking bk = second.bookings.getById(1);
                eq(BookingStatus.IN_PROGRESS, bk.status());
                eq(700L, bk.total());
                eq(2, bk.items().size());
                check(bk.startedAt() != null);
                eq(Instant.parse("2026-11-07T11:00:00Z"), bk.createdAt());
            }
        } finally {
            deleteTree(dir);
        }
    }

    private static void idsNotReused() throws IOException {
        Path dir = Files.createTempDirectory("carwash-test");
        try {
            try (Env first = new Env(dir)) {
                first.customers.register("One Person", "0711111111");
                Customer two = first.customers.register("Two Person", "0722222222");
                eq(DeleteOutcome.DELETED, first.customers.delete(two.id()));
            }
            try (Env second = new Env(dir)) {
                Customer three = second.customers.register("Three Person", "0733333333");
                eq(3, three.id());   // 2 was used and deleted, so it is never handed out again
            }
        } finally {
            deleteTree(dir);
        }
    }

    private static void corruptLine() throws IOException {
        Path dir = Files.createTempDirectory("carwash-test");
        try {
            try (Env first = new Env(dir)) {
                first.customers.register("Good Person", "0711111111");
            }
            Path file = dir.resolve("customers.txt");
            List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
            lines.add("this is not a valid record");
            lines.add("7|Bad Date|+254722222222|not-a-date|true");
            Files.write(file, lines, StandardCharsets.UTF_8);

            CustomerRepository repo = new CustomerRepository(new FileStore(dir));
            repo.load();
            eq(1, repo.all().size());
            eq(1, repo.warnings().size());
            Path backups = dir.resolve("backup");
            check(Files.isDirectory(backups));
            try (Stream<Path> s = Files.list(backups)) {
                check(s.anyMatch(p -> p.getFileName().toString().startsWith("customers.txt.corrupt-")));
            }
            // the file was rewritten clean, so the warning does not repeat
            CustomerRepository again = new CustomerRepository(new FileStore(dir));
            again.load();
            eq(0, again.warnings().size());
            eq(1, again.all().size());
        } finally {
            deleteTree(dir);
        }
    }

    private static void locking() throws IOException {
        Path dir = Files.createTempDirectory("carwash-test");
        FileStore a = new FileStore(dir);
        FileStore b = new FileStore(dir);
        try {
            check(a.acquireLock());
            check(!b.acquireLock());
            a.close();
            check(b.acquireLock());
        } finally {
            a.close();
            b.close();
            deleteTree(dir);
        }
    }

    private static void orphans() throws IOException {
        try (Env e = env()) {
            Customer a = e.customers.register("Amina Hassan", "0712345678");
            Vehicle v = e.vehicles.add("KAA 123A", VehicleType.CAR, a.id());
            e.bookings.create(a.id(), v.id(), List.of(1));
            eq(0, IntegrityChecker.check(e.customerRepo, e.vehicleRepo, e.bookingRepo).size());
            e.customerRepo.remove(a.id());   // simulate damage by bypassing the service rules
            eq(2, IntegrityChecker.check(e.customerRepo, e.vehicleRepo, e.bookingRepo).size());
        }
    }

    private static void logMasking() {
        eq("Call 0712***678 now", AppLog.mask("Call 0712345678 now"));
        eq("+254***678", AppLog.mask("+254712345678"));
        eq("no number here", AppLog.mask("no number here"));
    }

    // ---- tiny test toolkit ----

    private static Env env() throws IOException {
        return new Env(Files.createTempDirectory("carwash-test"));
    }

    private interface Body {
        void run() throws Exception;
    }

    private static void run(String name, Body body) {
        try {
            body.run();
            passed++;
            System.out.println("PASS  " + name);
        } catch (Throwable t) {
            failed++;
            System.out.println("FAIL  " + name + "  ->  " + t);
            StackTraceElement[] st = t.getStackTrace();
            for (int i = 0; i < Math.min(4, st.length); i++) {
                System.out.println("        at " + st[i]);
            }
        }
    }

    private static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("Condition was false");
        }
    }

    private static void eq(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static <E extends AppException> E expect(Class<E> type, Body body) {
        try {
            body.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
            throw new AssertionError("Expected " + type.getSimpleName() + " but got " + t);
        }
        throw new AssertionError("Expected " + type.getSimpleName() + " but nothing was thrown");
    }

    private static void throwsA(Class<? extends Throwable> type, Body body) {
        try {
            body.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) {
                return;
            }
            throw new AssertionError("Expected " + type.getSimpleName() + " but got " + t);
        }
        throw new AssertionError("Expected " + type.getSimpleName() + " but nothing was thrown");
    }

    private static void deleteTree(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }
}
