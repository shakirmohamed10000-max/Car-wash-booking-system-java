public class Vehicle {

    private String plateNumber;
    private String vehicleType;
    private String ownerName;
    private double washPrice;

    public Vehicle(String plateNumber, String vehicleType,
                   String ownerName) {

        this.plateNumber = plateNumber;
        this.vehicleType = vehicleType;
        this.ownerName = ownerName;

        this.washPrice = calculatePrice(vehicleType);
    }

    private double calculatePrice(String type) {

        switch (type.toLowerCase()) {

            case "bike":
                return 100;

            case "car":
                return 300;

            case "suv":
                return 400;

            case "truck":
                return 600;

            default:
                return 0;
        }
    }

    public void displayVehicle() {

        System.out.println("----------------------------");
        System.out.println("Vehicle Plate: " + plateNumber);
        System.out.println("Vehicle Type: " + vehicleType);
        System.out.println("Owner Name: " + ownerName);
        System.out.println("Wash Price: KSh " + washPrice);
        System.out.println("----------------------------");
    }

    public String getPlateNumber() {
        return plateNumber;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public double getWashPrice() {
        return washPrice;
    }
}
