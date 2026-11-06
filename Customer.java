public class Customer {

    // Customer details
    private int id;
    private String name;
    private String phone;

    // Constructor to add a new customer
    public Customer(int id, String name, String phone) {
        this.id = id;
        this.name = name;
        this.phone = phone;
    }

    // Get customer ID
    public int getId() {
        return id;
    }

    // Get customer name
    public String getName() {
        return name;
    }

    // Get customer phone number
    public String getPhone() {
        return phone;
    }

    // Display customer information
    public String toString() {
        return "Customer ID: " + id +
               ", Name: " + name +
               ", Phone: " + phone;
    }
}
