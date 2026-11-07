package ui;

import app.Config;
import java.io.PrintStream;
import java.util.List;

/** Top-level menu. Leaving it ends the program. */
public final class MainMenu extends Menu {

    private final CustomerMenu customerMenu;
    private final VehicleMenu vehicleMenu;
    private final ServiceMenu serviceMenu;
    private final BookingMenu bookingMenu;

    public MainMenu(InputReader in, PrintStream out, CustomerMenu customerMenu, VehicleMenu vehicleMenu,
                    ServiceMenu serviceMenu, BookingMenu bookingMenu) {
        super(in, out);
        this.customerMenu = customerMenu;
        this.vehicleMenu = vehicleMenu;
        this.serviceMenu = serviceMenu;
        this.bookingMenu = bookingMenu;
    }

    public void run() {
        loop(Config.BUSINESS_NAME + " - main menu", "Exit", List.of(
                new Item("Customers", customerMenu::run),
                new Item("Vehicles", vehicleMenu::run),
                new Item("Wash services", serviceMenu::run),
                new Item("Bookings", bookingMenu::run)));
        out.println("Goodbye.");
    }
}
