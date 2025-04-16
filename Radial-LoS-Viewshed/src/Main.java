import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;


import javax.imageio.ImageIO;

public class Main {

    public static void main(String[] args) {

        double lon = 23.5032;
        double lat = 38.5342;
        double elevation = 0;
        double distance = 50000;
        double targetElevation = 0;
        int mode = 1;
        int numThreads = 8; //Runtime.getRuntime().availableProcessors(); // Get available CPU cores

        viewshed(lon,lat,elevation,distance,targetElevation,mode,numThreads);

    }

    public static void viewshed(double longitude, double latitude, double observerHeight, double radius, double targetHeight, int mode, int numThreads) {


        double minShift = -0.00013888888;
        double maxShift = 0.00013888888889;

        PointOfInterest observer = new PointOfInterest(longitude,latitude,observerHeight);


        int singleArraySize = 3601;
        RasterUtils ru = new RasterUtils(singleArraySize);
        SpatialUtils su = new SpatialUtils(ru);
        // get the most northern cell
        double[] north = su.calculateFarthestPoint(latitude, longitude, 0.0, radius);
        // get the most eastern cell
        double[] east = su.calculateFarthestPoint(latitude, longitude, 90.0, radius);
        // get the most southern cell
        double[] south = su.calculateFarthestPoint(latitude, longitude, 180.0, radius);
        // get the most western cell
        double[] west = su.calculateFarthestPoint(latitude, longitude, 270.0, radius);

        double minLon = (int) west[1] + minShift;
        double maxLon = Math.ceil(east[1]) + maxShift;
        double minLat = (int)  south[0] + minShift;
        double maxLat = Math.ceil(north[0]) + maxShift;


        System.out.println("Minimum lon: " + minLon + " | Maximum lon: " + maxLon + " | Minimum lat: " + minLat + " | Maximum lat: " + maxLat);

        su.setGridBorders(minLon, maxLon, minLat, maxLat);

        int gridWidth = (int) (Math.floor(maxLon) - Math.ceil(minLon))*singleArraySize;
        int gridHeight = (int) (Math.floor(maxLat) - Math.ceil(minLat))*singleArraySize;

        su.setGridWidth(gridWidth);
        su.setGridHeight(gridHeight);
        su.initializeArray();
        System.out.println("Grid width: " + gridWidth + " | Grid height: " + gridHeight);


        // get the raster cell that corresponds to the observer
        double[] oxy = su.getXY(observer.getLongitude(),observer.getLatitude());

        System.out.println("Concatenating the following tif files...");

        long rasterStart = System.currentTimeMillis();
        Raster finalRaster = null;
        String filename = "";
        for (int x = getIntegerPart(west[1]); x<= getIntegerPart(east[1]); x++) {
            Raster verticalRaster = null;
            for (int y = getIntegerPart(north[0]); y >= getIntegerPart(south[0]); y=y-1) {
                try {
                    filename = String.format("dem/Copernicus_DSM_10_N%s_00_E0%s_00_DEM.tif",y,x);
                    File inputFile = new File(filename);
                    Raster image = ImageIO.read(inputFile).getData();
                    System.out.println(filename);
                    if (verticalRaster != null) {
                        verticalRaster = ru.concatenateRastersVertically(verticalRaster,image);
                    }
                    else {
                        verticalRaster = image;
                    }
                }
                catch (IOException e) {
                    System.out.println("File issue: " + filename);
                    // this means that a DEM file does not exist because in the surveillance region all elevation data are zero (sea level)
                    int[][] emptyDEM = new int[gridWidth][gridHeight];
                    verticalRaster = verticalRaster == null?ru.convertArrayToRaster(emptyDEM):ru.concatenateRastersVertically(verticalRaster,ru.convertArrayToRaster(emptyDEM));
                }
            }
            finalRaster = finalRaster == null?verticalRaster:ru.concatenateRastersHorizontally(finalRaster,verticalRaster);
        }
        long rasterEnd = System.currentTimeMillis();
        long rasterDuration = rasterEnd - rasterStart;
        System.out.println("Time taken to combine rasters: " + rasterDuration + " milliseconds (" + rasterDuration/1000.0 + " seconds)");

        long start = System.currentTimeMillis();


        System.out.println("Observer Lon/Lat: " + longitude + " | " + latitude);
        double rangeStart = 0.0;
        double rangeEnd = 360.0;
        ForkJoinPool pool = new ForkJoinPool(numThreads);
        // Parallel execution: each thread gets its own contiguous chunk of the range
        Raster finalRaster1 = finalRaster;
        pool.submit(() -> IntStream.range(0, numThreads).parallel().forEach(threadId -> {
            double chunkSize = (rangeEnd - rangeStart) / numThreads; // Divide range into equal parts
            double localStart = rangeStart + threadId * chunkSize;

            // calculate the first peripheral cell of the next thread as well
            double nextLocalStart = 0.0;
            if ((threadId) == (numThreads - 1)) {
                // if this thread is responsible for the last range, then the next local start is the local start of the first thread
                nextLocalStart = rangeStart + 0 * chunkSize;
            }
            else {
                // else the next local start is the local start of the next thread
                nextLocalStart = rangeStart + (threadId+1) * chunkSize;
            }
            double[] azimuthEndPoint = su.calculateFarthestPoint(latitude, longitude, nextLocalStart, radius-1);
            double[] azimuthEndPointXY = su.getXY(azimuthEndPoint[1], azimuthEndPoint[0]);
            RasterCell endPeripheralCell = new RasterCell((int) azimuthEndPointXY[0],(int) azimuthEndPointXY[1]);
            double endingAzimuth = su.calculateAzimuth(oxy[0], oxy[1], endPeripheralCell.getX(), endPeripheralCell.getY());

            double[] azimuthStartPoint = su.calculateFarthestPoint(latitude, longitude, localStart, radius-1);

            double[] azimuthStartPointXY = su.getXY(azimuthStartPoint[1], azimuthStartPoint[0]);
            RasterCell peripheralCell = new RasterCell((int) azimuthStartPointXY[0],(int) azimuthStartPointXY[1]);
            double startingAzimuth = su.calculateAzimuth(oxy[0], oxy[1], peripheralCell.getX(), peripheralCell.getY());


            System.out.println("Thread " + threadId + " processing range: " + startingAzimuth + " - " + endingAzimuth);

            parallelProcessViewshed(threadId,numThreads,su,peripheralCell,endPeripheralCell,observer,radius,oxy, finalRaster1,targetHeight,mode,startingAzimuth,endingAzimuth);
        })).join();

        pool.shutdown();



        long end = System.currentTimeMillis();
        long duration = end-start;
        System.out.println("Time taken to calculate 3D viewshed: " + duration + " milliseconds (" + duration/1000.0 + " seconds)");


        long imageStart = System.currentTimeMillis();
        ru.arrayToImage(su.getFinalArray(),su.gridWidth,su.gridHeight,minLon,maxLon,minLat,maxLat,"viewshed_" + mode);
        long imageEnd = System.currentTimeMillis();
        long imageDuration = imageEnd-imageStart;
        System.out.println("Time taken to export image: " + imageDuration + " milliseconds (" + imageDuration/1000.0 + " seconds)");
    }

    public static void parallelProcessViewshed(int threadId, int numThreads, SpatialUtils su, RasterCell peripheralCell, RasterCell endPeripheralCell, PointOfInterest observer,
                                               double radius, double[] oxy, Raster finalRaster, double targetHeight,
                                               int mode, double azimuthStart, double azimuthEnd) {
        double azimuth = azimuthStart;
        double previousAzimuth = azimuthStart;
        double totalChange = 0.0;
        HashSet<RasterCell> visited = new HashSet<>();
        //calculate visibility in observer's line of sight
        su.getVoxelTraversalLine(observer, peripheralCell, finalRaster, targetHeight, mode);
        // Mark the current cell as visited
        visited.add(peripheralCell);

        while (true) {

            // Perform the radial sweep to get the next cell
            peripheralCell = su.radialSweep(peripheralCell, observer, radius, azimuth, visited);
            // Recalculate azimuth for the next radial sweep
            azimuth = su.calculateAzimuth(oxy[0], oxy[1], peripheralCell.getX(), peripheralCell.getY());

            // Calculate the shortest angular difference
            double delta = (azimuth - previousAzimuth + 360) % 360;
            if (delta > 180) delta -= 360;  // Correct wraparound
            totalChange += Math.abs(delta);


            // Mark the current cell as visited
            visited.add(peripheralCell);
            //calculate visibility in observer's line of sight
            su.getVoxelTraversalLine(observer, peripheralCell, finalRaster, targetHeight, mode);

            if (threadId == numThreads - 1) {
                if (threadId == 0) { // if this is the first and only thread
                    if (totalChange >= 359.99) {
                        break;
                    }
                }
                else if (previousAzimuth > 358 && azimuth < 1) { // if this is the last thread (thread looking at the last portion of the circle)
                    break;
                }
            }
            else {
                if (azimuth > azimuthEnd) {
                    break;
                }
            }

            previousAzimuth = azimuth;

        }
    }

    /**
     * Get the integer part of a double number.
     * @param num The double number.
     * @return The integer part.
     */
    public static int getIntegerPart(double num) {
        String doubleAsString = String.valueOf(num);
        int indexOfDecimal = doubleAsString.indexOf(".");
        return Integer.parseInt(doubleAsString.substring(0, indexOfDecimal));
    }


}

