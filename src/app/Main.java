package app;

import exception.AppException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import service.BookingService;
import service.CustomerService;
import service.IntegrityChecker;
import service.ServiceCatalog;
import service.VehicleService;
import storage.BookingRepository;
import storage.CustomerRepository;
import storage.FileStore;
import storage.Repository;
import storage.ServiceRepository;
import storage.VehicleRepository;
import ui.BookingMenu;
import ui.CustomerMenu;
import ui.InputClosedException;
import ui.InputReader;
import ui.MainMenu;
import ui.Pickers;
import ui.ServiceMenu;
import ui.TablePrinter;
import ui.VehicleMenu;
import ui.Views;

/**
 * Entry point. Wires the layers together, loads the data files and starts the main menu.
 * Run it from the project root so the data/ folder is always the same one.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        AppLog.init(Config.logDir());

        FileStore store;
        try {
            store = new FileStore(Config.dataDir());
            if (!store.acquireLock()) {
                out.println("Another copy of this program is already using the data. Close it and try again.");
                System.exit(1);
                return;
            }
        } catch (AppException e) {
            out.println(e.getMessage());
            System.exit(1);
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(store::close));

        try {
            start(store, out);
        } catch (InputClosedException e) {
            out.println();
            out.println("Input closed. All changes were already saved. Exiting.");
        } catch (AppException e) {
            out.println(e.getMessage());
            System.exit(1);
        } catch (RuntimeException e) {
            AppLog.error("Fatal error", e);
            out.println("The program stopped because of an unexpected error. Details were written to the log.");
            System.exit(1);
        } finally {
            store.close();
        }
    }

    private static void start(FileStore store, PrintStream out) {
        Clock clock = Config.clock();

        CustomerRepository customerRepo = new CustomerRepository(store);
        VehicleRepository vehicleRepo = new VehicleRepository(store);
        ServiceRepository serviceRepo = new ServiceRepository(store);
        BookingRepository bookingRepo = new BookingRepository(store);
        List<Repository<?>> repos = List.of(customerRepo, vehicleRepo, serviceRepo, bookingRepo);
        repos.forEach(Repository::load);

        List<String> notices = new ArrayList<>();
        repos.forEach(r -> notices.addAll(r.warnings()));
        notices.addAll(IntegrityChecker.check(customerRepo, vehicleRepo, bookingRepo));

        ServiceCatalog catalog = new ServiceCatalog(serviceRepo, bookingRepo);
        catalog.seedDefaultsIfFirstRun();
        CustomerService customers = new CustomerService(customerRepo, vehicleRepo, bookingRepo, clock);
        VehicleService vehicles = new VehicleService(vehicleRepo, customerRepo, bookingRepo, clock);
        BookingService bookings = new BookingService(bookingRepo, customerRepo, vehicleRepo, serviceRepo, clock);

        InputReader in = new InputReader(System.in, out);
        TablePrinter table = new TablePrinter(out, in);
        Views views = new Views(out, table, customers, vehicles, bookings);
        Pickers pickers = new Pickers(in, out, views, customers, vehicles, bookings);

        MainMenu menu = new MainMenu(in, out,
                new CustomerMenu(in, out, customers, views, pickers),
                new VehicleMenu(in, out, vehicles, customers, views, pickers),
                new ServiceMenu(in, out, catalog, views),
                new BookingMenu(in, out, bookings, catalog, views, pickers));

        out.println(Config.BUSINESS_NAME);
        if (!notices.isEmpty()) {
            out.println();
            out.println("Data check:");
            notices.forEach(n -> out.println("  " + n));
            out.println("  Details are in the log file.");
        }
        menu.run();
    }
}
