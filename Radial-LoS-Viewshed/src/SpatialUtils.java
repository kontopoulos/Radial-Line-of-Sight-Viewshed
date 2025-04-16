import java.awt.image.Raster;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

public class SpatialUtils {

    public int R = 6371000;
    public double distanceDegreeLat = 111320; // distance of 1 degree of latitude
    private double minLon;
    private double minLat;
    private double maxLon;
    private double maxLat;
    private int[][] finalArray;
    private final RasterUtils rasterUtils;
    public int gridWidth;
    public int gridHeight;

    public SpatialUtils(RasterUtils ru) {
        this.rasterUtils = ru;
    }

    public void setGridWidth(int gridWidth) {
        this.gridWidth = gridWidth;
    }

    public void setGridHeight(int gridHeight) {
        this.gridHeight = gridHeight;
    }

    public void setGridBorders(double minLon, double maxLon, double minLat, double maxLat) {
        this.minLon = minLon;
        this.maxLon = maxLon;
        this.minLat = minLat;
        this.maxLat = maxLat;
    }

    public void initializeArray() {
        finalArray = new int[gridWidth][gridHeight];
        for (int i = 0; i < gridHeight; i++) {
            Arrays.fill(finalArray[i], 0x00000000); // fully transparent
        }
    }

    public int[][] getFinalArray() {
        return finalArray;
    }

    /**
     * Get the angle between the observer and the cell
     * @param observer
     * @param cell
     * @return angle in degrees
     */
    public double getAngleOfElevation(PointOfInterest observer, Cell cell, Double distance) {
        double hc = getAdjustedHeight(distance);
        double zi = cell.getHeight()-hc;
        double deltaZ = zi-observer.getHeight();
        return Math.atan(deltaZ/distance);
    }

    /**
     * Check whether the visibility is blocked based on the cell's current angle
     * @param maxAngle
     * @param currentAngle
     * @return
     */
    public boolean isVisible(double maxAngle, double currentAngle) {
        if (currentAngle > maxAngle) return true;
        else if (currentAngle <= maxAngle) return false;
        else return false;
    }

    /**
     * Calculate the height of the point given the
     * distance from the observer and account for earth's curvature
     * @param distance distance from the observer
     * @return adjusted height due to earth's curvature
     */
    private double getAdjustedHeight(double distance) {
        return R-Math.sqrt(Math.pow(R,2.0)-Math.pow(distance,2.0));
    }

    private double haversine(double val) {
        return Math.pow(Math.sin(val / 2), 2);
    }

    /**
     * Calculates the haversine distance
     * @param lat1
     * @param lon1
     * @param lat2
     * @param lon2
     * @return distance in meters
     */
    public double getHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians((lat2 - lat1));
        double dLong = Math.toRadians((lon2 - lon1));
        lat1 = Math.toRadians(lat1);
        lat2 = Math.toRadians(lat2);
        double a = haversine(dLat) + Math.cos(lat1) * Math.cos(lat2) * haversine(dLong);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /**
     * Given coordinates of a point return the pixel coordinates where the point is located in the DEM file
     * @param lon longitude
     * @param lat latitude
     * @return
     */
    public double[] getXY(double lon, double lat) {
        double maxDistX = maxLon-minLon;
        double maxDistY = maxLat-minLat;
        double distX = lon-minLon;
        double distY = lat-minLat;
        double percX = distX/maxDistX;
        double percY = distY/maxDistY;
        return new double[]{gridWidth*percX, gridHeight - gridHeight*percY};
    }

    public double[] getLonLat(double X, double Y) {

        double stepX = (maxLon - minLon) / gridWidth;
        double stepY = (maxLat - minLat) / gridHeight;

        return new double[]{minLon+X*stepX,maxLat-Y*stepY};
    }

    public double getHeightFromCoordinates(Raster raster, double lon, double lat) {
        int latInt = getIntegerPart(lat);
        int lonInt = getIntegerPart(lon);
        double[] xy = getXY(lon,lat);
        return raster.getSampleDouble(lonInt, latInt, 0); // 0 for the first (and only) band in grayscale
    }

    public double getHeightFromRaster(Raster raster, double x, double y) {
        try {
            return raster.getSampleDouble((int) x, (int) y, 0);
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Calculate the height required to stay visible
     * @param observer
     * @param distance distance of cell from observer
     * @param maxAngle maximum angle encountered so far
     * @return
     */
    public double getVisibilityHeight(PointOfInterest observer, double distance, double maxAngle) {
        double z = observer.getHeight() + distance*Math.tan(maxAngle);
        // adjust height to include earth's curvature
        return z + getAdjustedHeight(distance);
    }

    /**
     * Get the distance of 1 degree of longitude based on the given latitude
     * @param latitude
     * @return
     */
    public double getDistancePerDegreeOfLon(double latitude) {
        return distanceDegreeLat*Math.cos(latitude);
    }

    /**
     * Get the integer part of a double number.
     * @param num The double number.
     * @return The integer part.
     */
    public int getIntegerPart(double num) {
        String doubleAsString = String.valueOf(num);
        int indexOfDecimal = doubleAsString.indexOf(".");
        return Integer.parseInt(doubleAsString.substring(0, indexOfDecimal));
    }

    /**
     * Given a point, it calculates the farthest point
     * based on the given bearing and max distance
     * @param lat
     * @param lon
     * @param bearing
     * @param distance
     * @return point in lat,lon array
     */
    public double[] calculateFarthestPoint(double lat, double lon, double bearing, double distance){
        // Convert latitude, longitude, and bearing to radians
        double latRad = Math.toRadians(lat);
        double lonRad = Math.toRadians(lon);
        double bearingRad = Math.toRadians(bearing);
        // Calculate the destination latitude
        double lat2 = Math.asin(Math.sin(latRad) * Math.cos(distance / R) +
                Math.cos(latRad) * Math.sin(distance / R) * Math.cos(bearingRad));
        // Calculate the destination longitude
        double lon2 = lonRad + Math.atan2(Math.sin(bearingRad) * Math.sin(distance / R) * Math.cos(latRad),
                Math.cos(distance / R) - Math.sin(latRad) * Math.sin(lat2));
        // Convert the results back to degrees
        return new double[] {Math.toDegrees(lat2), Math.toDegrees(lon2)};
    }

    /**
     *
     * @param observer
     * @param target
     * @param image
     * @param mode type of generated viewshed
     * @return
     */
    public void getVoxelTraversalLine(PointOfInterest observer, RasterCell target, Raster image, double elevationTarget, int mode) {
        double[] xy = getXY(observer.getLongitude(),observer.getLatitude());

        double x1 = xy[0];
        double y1 = xy[1];
        double x2 = target.getX();
        double y2 = target.getY();

        double x = x1;
        double y = y1;

        double thetaMax = -1.6;

        double deltaX = x2-x1;
        double deltaY = y2-y1;

        double stepX = Math.signum(deltaX);
        double stepY = Math.signum(deltaY);

        //Ray/Slope related maths
        //Straight distance to the first vertical grid boundary.
        double xOffset = x2 > x1 ? (Math.ceil(x1) - x1) : (x1 - Math.floor(x1));
        //Straight distance to the first horizontal grid boundary.
        double yOffset = y2 > y1 ? (Math.ceil(y1) - y1) : (y1 - Math.floor(y1));
        //Angle of ray/slope.
        double angle = Math.atan2(-deltaY, deltaX);
        //How far to move along the ray to cross the first vertical grid cell boundary.
        double tMaxX = xOffset / Math.cos(angle);
        //How far to move along the ray to cross the first horizontal grid cell boundary.
        double tMaxY = yOffset / Math.sin(angle);
        //How far to move along the ray to move horizontally 1 grid cell.
        double tDeltaX = 1.0 / Math.cos(angle);
        //How far to move along the ray to move vertically 1 grid cell.
        double tDeltaY = 1.0 / Math.sin(angle);

        //Travel one grid cell at a time.
        double manhattanDistance = Math.abs(Math.floor(x2) - Math.floor(x1)) + Math.abs(Math.floor(y2) - Math.floor(y1));
        for (double t = 0; t <= manhattanDistance; ++t) {

            double[] lonLat = getLonLat((int) x, (int) y);
            Cell intermediateCell = new Cell(lonLat[0],lonLat[1],getHeightFromRaster(image,x,y));
            double distance = getHaversineDistance(observer.getLatitude(),observer.getLongitude(),intermediateCell.getLatitude(),intermediateCell.getLongitude());
            double theta = getAngleOfElevation(observer,intermediateCell,distance);
            switch (mode) {
                // generates heightmap visualizing for every cell in the radius the height required to stay visible from the observer
                case 0: {
                    // calculate height to stay visible from the observer
                    int visibilityHeight = (int) getVisibilityHeight(observer, distance, thetaMax);
                    intermediateCell.addVisibilityHeight(visibilityHeight);
                    int color = rasterUtils.getColor(visibilityHeight);
                    finalArray[(int) x][(int) y] = color;
                    break;
                }
                case 1: // generates the cells that are visible from the observer
                {
                    if (isVisible(thetaMax, theta)) finalArray[(int) x][(int) y] = 0xFFFF0000;//los.add(intermediateCell);
                    break;
                }
                case 2: // generates the cells that the observer sees at specific target height
                {
                    // calculate height to stay visible from the observer
                    int visibilityElevation = (int) getVisibilityHeight(observer,distance,thetaMax);
                    if (visibilityElevation >= elevationTarget) {
                        intermediateCell.addVisibilityHeight(visibilityElevation);
                        int color = rasterUtils.getColor(visibilityElevation);
                        finalArray[(int) x][(int) y] = color;
                    }
                    break;
                }
                case 3: // checks whether the target is visible from the observer
                {
                    if (isVisible(thetaMax, theta)) {
                        RasterCell intermediateRasterCell = new RasterCell((int) x, (int) y);
                        if (intermediateRasterCell.equals(target))
                            finalArray[(int) x][(int) y] = 255;//los.add(intermediateCell);
                    }
                    break;
                }

            }
            thetaMax = Math.max(theta, thetaMax);

            //Only move in either X or Y coordinates, not both.
            if (Math.abs(tMaxX) < Math.abs(tMaxY)) {
                tMaxX += tDeltaX;
                x += stepX;
            } else {
                tMaxY += tDeltaY;
                y += stepY;
            }
        }
    }

    public double calculateAzimuth(double x1, double y1, double x2, double y2) {
        // Calculate the differences
        double dx = x2 - x1;
        double dy = (gridHeight - y2) - (gridHeight - y1);

        // Calculate the azimuth in radians
        double azimuthRadians = Math.atan2(dx, dy);

        // Convert radians to degrees
        double azimuthDegrees = Math.toDegrees(azimuthRadians);

        // Normalize to 0–360 degrees
        if (azimuthDegrees < 0) {
            azimuthDegrees += 359.99;
        }
        return azimuthDegrees;
    }

    public RasterCell radialSweep(RasterCell rc, PointOfInterest o, double distance, double bearing, HashSet<RasterCell> visited) {
        // Map of bearing ranges to search for
        int[][] directions;
        // search in different directions based on the azimuth of the current cell and the observer
        if (bearing >= 0.0 && bearing <= 90.0) {
            directions = new int[][] {
                    { 1,  0}, // Right
                    { 1,  1}, // Bottom-right
                    { 0,  1},  // Bottom
                    { -1, 1} // Bottom-left
            };
        } else if (bearing > 90.0 && bearing <= 180.0) {
            directions = new int[][] {
                    { 0,  1}, // Bottom
                    { -1, 1}, // Bottom-left
                    { -1, 0},  // Left
                    {-1, -1} // Top-left
            };
        } else if (bearing > 180.0 && bearing <= 270.0) {
            directions = new int[][] {
                    { -1, 0}, // Left
                    {-1, -1}, // Top-left
                    { 0, -1},  // Top
                    { 1, -1} // Top-right
            };
        } else {
            directions = new int[][] {
                    { 0, -1}, // Top
                    { 1, -1}, // Top-right
                    { 1,  0},  // Right
                    { 1,  1} // Bottom-right
            };
        }
        // search the cell in the respective directions
        for (int[] dir : directions) {
            RasterCell newRc = new RasterCell(rc.getX() + dir[0], rc.getY() + dir[1]);

            double[] gc = getLonLat(newRc.getX(), newRc.getY());
            if (getHaversineDistance(o.getLatitude(), o.getLongitude(), gc[1], gc[0]) <= distance && !visited.contains(newRc)) {
                return newRc;
            }
        }
        // Return the original cell if no valid new cell is found
        return rc;
    }

}
