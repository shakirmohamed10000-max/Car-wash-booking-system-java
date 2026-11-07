package model;

/** The kinds of vehicle the wash accepts. An enum makes an invalid type impossible. */
public enum VehicleType {
    BIKE("Bike"),
    CAR("Car"),
    SUV("SUV"),
    TRUCK("Truck");

    private final String label;

    VehicleType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Accepts the constant name or the label, in any case. */
    public static VehicleType parse(String text) {
        if (text != null) {
            String t = text.trim();
            for (VehicleType type : values()) {
                if (type.name().equalsIgnoreCase(t) || type.label.equalsIgnoreCase(t)) {
                    return type;
                }
            }
        }
        throw new IllegalArgumentException("Unknown vehicle type.");
    }
}
