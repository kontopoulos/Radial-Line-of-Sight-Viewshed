import java.util.ArrayList;
import java.util.Objects;

public class Cell {

    private final double longitude;
    private final double latitude;
    private final double height;
    private double angle;
    private ArrayList<Double> visibilityHeights;

    public Cell(double longitude, double latitude, double height) {
        this.longitude = longitude;
        this.latitude = latitude;
        this.height = height;
        this.visibilityHeights = new ArrayList<>();
    }

    public double getLongitude() {
        return longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getHeight() {
        return height;
    }

    public double getAngle() {
        return angle;
    }

    public void setAngle(double angle) {
        this.angle = angle;
    }

    public ArrayList<Double> getVisibilityHeights() {
        return visibilityHeights;
    }

    public void addVisibilityHeight(double height) {
        this.visibilityHeights.add(height);
    }

    public double getMeanVisibilityHeight() {
        if (this.visibilityHeights.isEmpty()) return 0.0;
        // Calculate the sum using a stream or loop
        double sum = 0.0;
        for (double num : this.visibilityHeights) {
            sum += num;
        }
        // Calculate and return the mean
        return sum / this.visibilityHeights.size();
    }

    public String toWKT(double stepLon, double stepLat) {
        return hashCode() + "|" + getMeanVisibilityHeight() + "|POLYGON ((" + longitude + " " + latitude + ", " + (longitude + stepLon) + " " + latitude + ", " + (longitude + stepLon) + " " + (latitude - stepLat) + ", " + longitude + " " + (latitude - stepLat) + "))";
    }

    public String toCSV() {
        return hashCode() + "|" + getMeanVisibilityHeight() + "|" + longitude + "|" + latitude;
    }

    @Override
    public String toString() {
        return "{\"geometry\": {" +
                "\"type\": \"Point\"," +
                "\"coordinates\": [" + longitude + ", " + latitude + "]" +
                "}," +
                "\"properties\": {" +
                "\"height\":" + height + "," +
                "\"visibility_height\":" + getMeanVisibilityHeight() +
                "}\n}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Cell cell = (Cell) o;
        return longitude == cell.longitude && latitude == cell.latitude && height == cell.height;
    }

    @Override
    public int hashCode() {
        return Objects.hash(longitude, latitude);
    }
}
