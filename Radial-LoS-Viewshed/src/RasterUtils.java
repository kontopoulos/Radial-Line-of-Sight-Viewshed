import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.*;
import java.util.HashMap;
import java.util.stream.IntStream;

public class RasterUtils {

    private int size;
    private final int rangeStart = 0;
    private final int rangeEnd = 5000;
    // Create the HashMap to store the mapping
    private final HashMap<Integer, Integer> colorMap;

    public RasterUtils(int size) {
        this.size = size;
        colorMap  = new HashMap<>();
        // Populate the HashMap
        for (int i = rangeStart; i <= rangeEnd; i++) {
            colorMap.put(i, getInterpolatedColor(i));
        }
    }

    public int getColor(int height) {
        return colorMap.getOrDefault(height, 0x00000000);
    }

    public int getRangeStart() {
        return rangeStart;
    }

    public int getRangeEnd() {
        return rangeEnd;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    /**
     * Returns the interpolated ARGB color for a given value in the range [0, 5000].
     */
    private int getInterpolatedColor(int value) {
        // Define the milestones with their corresponding colors
        int[] milestones = {0, 10, 20, 60, 180, 540, 2000};
        int[] colors = {
                0xFFFF0000, // Red
                0xFFFFA500, // Orange
                0xFFFFFF00, // Yellow
                0xFF00FF00, // Green
                0xFF00FFFF, // Cyan
                0xFF0000FF, // Blue
                0xFF800080  // Purple
        };

        // Find which milestone interval the value belongs to
        for (int i = 0; i < milestones.length - 1; i++) {
            int start = milestones[i];
            int end = milestones[i + 1];

            if (value >= start && value <= end) {
                // Interpolate between colors[i] and colors[i + 1]
                return interpolateColor(colors[i], colors[i + 1], value, start, end);
            }
        }

        // If no match (shouldn't happen), return transparent
        return 0x00000000;
    }

    /**
     * Interpolates linearly between two colors based on the value's position in the range.
     */
    private int interpolateColor(int colorStart, int colorEnd, int value, int rangeStart, int rangeEnd) {
        // Normalize the position of the value within the range
        double ratio = (double) (value - rangeStart) / (rangeEnd - rangeStart);

        // Extract ARGB components
        int alphaStart = (colorStart >> 24) & 0xFF;
        int redStart = (colorStart >> 16) & 0xFF;
        int greenStart = (colorStart >> 8) & 0xFF;
        int blueStart = colorStart & 0xFF;

        int alphaEnd = (colorEnd >> 24) & 0xFF;
        int redEnd = (colorEnd >> 16) & 0xFF;
        int greenEnd = (colorEnd >> 8) & 0xFF;
        int blueEnd = colorEnd & 0xFF;

        // Interpolate each component
        int alpha = (int) (alphaStart + ratio * (alphaEnd - alphaStart));
        int red = (int) (redStart + ratio * (redEnd - redStart));
        int green = (int) (greenStart + ratio * (greenEnd - greenStart));
        int blue = (int) (blueStart + ratio * (blueEnd - blueStart));

        // Combine the components back into a single ARGB color
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    public Raster concatenateRastersVertically(Raster raster1, Raster raster2) {
        // Get dimensions
        int width = raster1.getWidth();
        int height1 = raster1.getHeight();
        int height2 = raster2.getHeight();
        // Create a new raster for the concatenated result
        WritableRaster concatenatedRaster = WritableRaster.createBandedRaster(
                java.awt.image.DataBuffer.TYPE_BYTE, width, height1 + height2, 1, null
        );

        // Copy data from the first raster
        for (int y = 0; y < height1; y++) {
            for (int x = 0; x < width; x++) {
                int value = raster1.getSample(x, y, 0);
                concatenatedRaster.setSample(x, y, 0, value);
            }
        }

        // Copy data from the second raster
        for (int y = 0; y < height2; y++) {
            for (int x = 0; x < width; x++) {
                int value = raster2.getSample(x, y, 0);
                concatenatedRaster.setSample(x, y + height1, 0, value); // Offset by height1
            }
        }

        return concatenatedRaster;
    }

    public Raster concatenateRastersHorizontally(Raster raster1, Raster raster2) {
        // Get dimensions
        int width1 = raster1.getWidth();
        int height1 = raster1.getHeight();
        int width2 = raster2.getWidth();
        int height2 = raster2.getHeight();

        // The new raster's width is the sum of the two widths
        int newWidth = width1 + width2;
        // The height is the maximum height of the two rasters
        int newHeight = Math.max(height1, height2);

        // Create a new WritableRaster for the concatenated result
        WritableRaster concatenatedRaster = WritableRaster.createBandedRaster(
                java.awt.image.DataBuffer.TYPE_BYTE, newWidth, newHeight, 1, null
        );

        // Fill the new raster with pixel data from raster1
        for (int y = 0; y < height1; y++) {
            for (int x = 0; x < width1; x++) {
                double value = raster1.getSampleDouble(x, y, 0);
                concatenatedRaster.setSample(x, y, 0, value);
            }
        }

        // Fill the new raster with pixel data from raster2
        for (int y = 0; y < height2; y++) {
            for (int x = 0; x < width2; x++) {
                double value = raster2.getSampleDouble(x, y, 0);
                concatenatedRaster.setSample(x + width1, y, 0, value); // Offset by width1
            }
        }
        return concatenatedRaster;
    }

    public Raster convertArrayToRaster(int[][] array) {
        int width = array.length;
        int height = array[0].length;
        // Create a WritableRaster with the desired dimensions
        WritableRaster raster = WritableRaster.createBandedRaster(
                java.awt.image.DataBuffer.TYPE_INT,width, height, 4, null
        );
        // Populate the raster with data from the 2D array
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                raster.setSample(x, y, 0, array[x][y]);
            }
        }
        return raster;
    }

    public void rasterToImage(Raster raster, String name) {
        int width = raster.getWidth();
        int height = raster.getHeight();

        // Create new BufferedImage for visualization
        BufferedImage concatenatedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        concatenatedImage.setData(raster);
        // Save the concatenated image
        try {
            ImageIO.write(concatenatedImage, "png", new File( "E:\\" + name + ".png"));
            System.out.println("Concatenated raster saved as " + name + ".png");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void arrayToImage(int[][] argbArray, int width, int height, double minLon, double maxLon, double minLat, double maxLat, String name) {
        // Create a BufferedImage with a writable INT_ARGB data buffer
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        WritableRaster raster = image.getRaster();
        DataBufferInt buffer = (DataBufferInt) raster.getDataBuffer();

        int[] pixels = buffer.getData(); // Direct reference to the internal pixel array

        // Parallel processing using IntStream.range().parallel()
        IntStream.range(0, width * height).parallel().forEach(i -> {
            int x = i % width;
            int y = i / width;
            pixels[i] = argbArray[x][y]; // Directly modify pixel buffer
        });

        // Calculate pixel size
        double xPixelSize = (maxLon - minLon) / width;
        double yPixelSize = (maxLat - minLat) / height; // Negative for north-up
        // World file content
        String worldFileContent = String.format(
                "%.10f\n" + // x pixel size
                        "0.0\n" +   // rotation (y-axis)
                        "0.0\n" +   // rotation (x-axis)
                        "%.10f\n" + // y pixel size (negative)
                        "%.10f\n" + // upper-left x-coordinate
                        "%.10f\n",  // upper-left y-coordinate
                xPixelSize,
                -yPixelSize,
                minLon,
                maxLat
        );
        try {
            ImageIO.write(image, "png", new File( name + ".png"));
            // Write to the world file
            File worldFile = new File(name + ".pgw");
            FileWriter writer = new FileWriter(worldFile);
            writer.write(worldFileContent);
            writer.close();
            System.out.println("Viewshed saved as " + name + ".png");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}


