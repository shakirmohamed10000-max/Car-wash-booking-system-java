package model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * A wash package with one price per vehicle type (Option A pricing).
 * A type with no entry in the price map is simply not offered for that service.
 *
 * @param prices whole shillings per vehicle type
 */
public record WashService(int id, String name, int durationMinutes, boolean active, Map<VehicleType, Long> prices) {

    public WashService {
        if (id <= 0) {
            throw new IllegalArgumentException("Invalid service id.");
        }
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(prices, "prices");
        if (name.isBlank() || durationMinutes <= 0) {
            throw new IllegalArgumentException("Invalid service.");
        }
        EnumMap<VehicleType, Long> copy = new EnumMap<>(VehicleType.class);
        copy.putAll(prices);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("A service needs at least one price.");
        }
        for (Long p : copy.values()) {
            if (p == null || p <= 0) {
                throw new IllegalArgumentException("Prices must be positive.");
            }
        }
        prices = Collections.unmodifiableMap(copy);
    }

    public OptionalLong priceFor(VehicleType type) {
        Long p = prices.get(type);
        return p == null ? OptionalLong.empty() : OptionalLong.of(p);
    }

    public boolean offeredFor(VehicleType type) {
        return prices.containsKey(type);
    }

    public WashService withName(String newName) {
        return new WashService(id, newName, durationMinutes, active, prices);
    }

    public WashService withDuration(int minutes) {
        return new WashService(id, name, minutes, active, prices);
    }

    public WashService withActive(boolean newActive) {
        return new WashService(id, name, durationMinutes, newActive, prices);
    }

    public WashService withPrices(Map<VehicleType, Long> newPrices) {
        return new WashService(id, name, durationMinutes, active, newPrices);
    }
}
