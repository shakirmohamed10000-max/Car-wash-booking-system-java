package storage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import model.Customer;

/** Stores customers in customers.txt. */
public final class CustomerRepository extends Repository<Customer> {

    public CustomerRepository(FileStore store) {
        super(store, "customers.txt", "id,name,phone,createdAt,active");
    }

    @Override
    protected int idOf(Customer c) {
        return c.id();
    }

    @Override
    protected List<String> toFields(Customer c) {
        return List.of(String.valueOf(c.id()), c.name(), c.phone(), c.createdAt().toString(), String.valueOf(c.active()));
    }

    @Override
    protected Customer fromFields(List<String> f) {
        expect(f, 5);
        return new Customer(Integer.parseInt(f.get(0)), f.get(1), f.get(2), Instant.parse(f.get(3)), parseBool(f.get(4)));
    }

    /** Looks across active and archived customers. */
    public Optional<Customer> findByPhone(String phone) {
        return all().stream().filter(c -> c.phone().equals(phone)).findFirst();
    }
}
