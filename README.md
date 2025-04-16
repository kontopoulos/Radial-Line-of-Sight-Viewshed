# Radial Line-of-Sight Viewshed Algorithm (also known as R3)

A Java-based multi-threaded implementation of the Radial Line-of-Sight (LoS) viewshed algorithm which is also known as R3.

## Requirements

The algorithm was implemented using plain Java (corretto 11) and no extra libraries are required. Therefore, it could be easily integrated with other services such as GeoServer, without worrying about dependencies.

## Digital Elevation Model (DEM) source

[https://prism-dem-open.copernicus.eu/pd-desk-open-access/prismDownload/COP-DEM_GLO-30-DGED__2022_1/Copernicus_DSM_10_N38_00_E022_00.tar](https://prism-dem-open.copernicus.eu/pd-desk-open-access/prismDownload/COP-DEM_GLO-30-DGED__2022_1/Copernicus_DSM_10_N38_00_E022_00.tar)

## High-Level Algorithm Implementation

### Input parameters

- Observer coordinates (lon/lat in EPSG:WGS4326 - WGS84).
- Observer's elevation in meters.
- Maximum distance in meters (radius) at which the LoS of the observer can reach.
- A Digital Elevation Model (DEM) in geotif.
- Number of threads.

### Output

- A png image and an associated pgw file that geotags the png image.

### Basic Steps of the Algorithm

- Given the observer's coordinates and the maximum radius, find the northernmost point (bearing of 0°) - edge of the viewing radius.
- Cast ray from the observer point outward towards the northernmost point.
- Walk cell by cell in a straight line from the observer to the edge of the viewing radius.
- For each ray, walk cell by cell in a straight line from the observer to the edge of the viewing radius.
- For each cell along the ray, calculate the elevation angle from the observer to that cell.
- Maintain the maximum elevation angle encountered so far on that ray.
- If the current cell's elevation angle is greater than the max angle so far, it's visible (and update the max angle).
- Otherwise, it’s not visible (occluded by higher terrain).
- Record each cell as either visible (1) or not visible (0) in the result raster.
- Next, given the cell in the edge of the viewing radius (initially at 0°) find the next edge cell by performing a radial sweep and calculating the distance of each cell from the observer. The cell should be withing the "radius" Haversine distance. Then, cast ray from the observer to the new edge cell and perform the ray casting.

## Variations (Modes) of the algorithm

Note that **mode 1** is the classic version of the viewshed algorithm that is commonly used.

| Mode | Description |
| ------ | ------ |
| 0 | Generates a RGB heightmap visualizing for every cell in the radius the height required to stay visible from the observer. Heights above a certain cut-off threshold are not visualized |
| 1 | Generates the cells in the terrain that are visible from the observer |
| 2 | Generates the cells that the observer sees at specific target height. This mode requires an extra input parameter called targetElevation |
| 3 | Checks whether the target is visible from the observer. The simplest form of LoS. This mode also requires an extra input parameter called targetElevation |

In the examples below, the red dot indicates the position of the observer.

### Example of mode 0

The heightmap follows the colors of the spectrum. The more red the color is, the lower the elevation is from which the observer can see in the respectivbe region. The more purple the color is, the higher the elevation is from which the observer can see in the respectivbe region. There are seven color values that are used as elevation thresholds that can be seen below. Colors in between these values correspond to elevation values between the elevation values presented below.

| Color Name | Hex Color Code | Elevation Value (in meters) |
| ------ | ------ | ------ |
| Red | 0xFFFF0000 |  0 |
| Orange | 0xFFFFA500 |  10 |
| Yellow | 0xFFFFFF00 |  20 |
| Green | 0xFF00FF00 |  60 |
| Cyan | 0xFF00FFFF |  100 |
| Blue | 0xFF0000FF |  540 |
| Purple | 0xFF800080 |  2000 |

Elevation heights above 2km are considered insignificant and are transparent the heightmap. The above thresholds can be changed to serve each user's specific needs.

![Viewshed mode 0](images/3d_viewshed.png)

### Example of mode 1

The red color indicates the terrain surface which is visible from the observer.

![Viewshed mode 1](images/terrain_viewshed.png)

## Further optimizations

- In the current implementation, the DEMs required to calculate the viewshed based on the radius given by the user are loaded into memory and concatenated into a single raster array. For a radius greater than 100km more memory needs to be allocated for the JVM. However, instead of loading and concatenating all the DEMs at once, each DEM could be loaded when needed. To do so, an algorithm needs to be implemented that finds the X/Y coordinates of the pixel in the respective DEM when the given radius exceeds the geographic limits of a single DEM, thus more DEMs than one are required to calculate the viewshed.

## License

BSD 3-Clause License
