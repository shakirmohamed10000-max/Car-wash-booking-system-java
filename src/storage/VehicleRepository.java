package storage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import model.Vehicle;
import model.VehicleType;

/** Stores vehicles in vehicles.txt. */
public final class VehicleRepository extends Repository<Vehicle> {

    public VehicleRepository(FileStore store) {
        super(store, "vehicles.txt", "id,plate,type,customerId,createdAt,active");
    }

    @Override
    protected int idOf(Vehicle v) {
        return v.id();
    }

    @Override
    protected List<String> toFields(Vehicle v) {
        return List.of(String.valueOf(v.id()), v.plate(), v.type().name(), String.valueOf(v.customerId()),
                v.createdAt().toString(), String.valueOf(v.active()));
    }

    @Override
    protected Vehicle fromFields(List<String> f) {
        expect(f, 6);
        return new Vehicle(Integer.parseInt(f.get(0)), f.get(1), VehicleType.parse(f.get(2)),
                Integer.parseInt(f.get(3)), Instant.parse(f.get(4)), parseBool(f.get(5)));
    }

    /** Looks across active and archived vehicles. */
    public Optional<Vehicle> findByPlate(String plate) {
        return all().stream().filter(v -> v.plate().equals(plate)).findFirst();
    }
}
