package ui;

import java.io.PrintStream;
import java.util.List;
import model.Customer;
import service.CustomerService;
import service.DeleteOutcome;
import service.Validators;

/** Customer menu: register, list, view, search, update, delete. */
public final class CustomerMenu extends Menu {

    private final CustomerService customers;
    private final Views views;
    private final Pickers pickers;

    public CustomerMenu(InputReader in, PrintStream out, CustomerService customers, Views views, Pickers pickers) {
        super(in, out);
        this.customers = customers;
        this.views = views;
        this.pickers = pickers;
    }

    public void run() {
        loop("Customers", "Back to main menu", List.of(
                new Item("Register customer", () -> {
                    begin("Register customer");
                    pickers.registerCustomer(null);
                }),
                new Item("List customers", () -> views.customerList(customers.list())),
                new Item("View customer", () -> {
                    begin("View customer");
                    views.customerDetail(pickers.pickCustomer(false));
                }),
                new Item("Search customers", () -> {
                    begin("Search customers");
                    views.customerList(customers.search(in.askNonBlank("Name or part of phone number")));
                }),
                new Item("Update customer", this::update),
                new Item("Delete customer", this::delete)));
    }

    private void update() {
        begin("Update customer");
        Customer c = pickers.pickCustomer(false);
        out.println("Press Enter to keep the current value.");
        String name = in.askOptionalValid("Name [" + c.name() + "]", Validators::cleanName);
        String phone = in.askOptionalValid("Phone [" + Validators.maskPhone(c.phone()) + "]", Validators::cleanPhone);
        if (name == null && phone == null) {
            out.println("Nothing changed.");
            return;
        }
        customers.update(c.id(), name, phone);
        out.println("Customer updated.");
    }

    private void delete() {
        begin("Delete customer");
        Customer c = pickers.pickCustomer(false);
        views.customerDetail(c);
        out.println();
        if (!in.confirmYes("Delete " + c.name() + "?")) {
            out.println("Nothing was deleted.");
            return;
        }
        DeleteOutcome result = customers.delete(c.id());
        if (result == DeleteOutcome.ARCHIVED) {
            out.println("Customer has booking history, so the record was archived instead of deleted.");
        } else {
            out.println("Customer deleted.");
        }
    }
}
