package storage;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import model.VehicleType;
import model.WashService;

/** Stores wash services in services.txt. Prices are written as BIKE=100;CAR=300. */
public final class ServiceRepository extends Repository<WashService> {

    public ServiceRepository(FileStore store) {
        super(store, "services.txt", "id,name,durationMinutes,active,prices");
    }

    @Override
    protected int idOf(WashService s) {
        return s.id();
    }

    @Override
    protected List<String> toFields(WashService s) {
        String prices = s.prices().entrySet().stream()
                .map(e -> e.getKey().name() + "=" + e.getValue())
                .collect(Collectors.joining(";"));
        return List.of(String.valueOf(s.id()), s.name(), String.valueOf(s.durationMinutes()),
                String.valueOf(s.active()), prices);
    }

    @Override
    protected WashService fromFields(List<String> f) {
        expect(f, 5);
        Map<VehicleType, Long> prices = new EnumMap<>(VehicleType.class);
        for (String pair : f.get(4).split(";")) {
            if (pair.isBlank()) {
                continue;
            }
            String[] kv = pair.split("=", -1);
            if (kv.length != 2) {
                throw new IllegalArgumentException("Bad price entry.");
            }
            prices.put(VehicleType.valueOf(kv[0]), Long.parseLong(kv[1]));
        }
        return new WashService(Integer.parseInt(f.get(0)), f.get(1), Integer.parseInt(f.get(2)),
                parseBool(f.get(3)), prices);
    }
}
