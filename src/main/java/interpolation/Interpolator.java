package interpolation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class Interpolator {

    protected int[][] ignoredCoordinates;
    protected HashMap<String, Float>[][] featuresCache;
    protected final HashSet<String> allFeatures;
    protected final int maxX;
    protected final int maxY;
    protected final int minX;
    protected final int minY;

    public Interpolator(HashMap<String, Float>[][] featuresCache,
                        HashSet<String> allFeatures, int maxX, int maxY, int minX, int minY, int[][] ignored) {

        this.featuresCache = featuresCache;
        this.allFeatures = allFeatures;
        this.maxX = maxX;
        this.maxY = maxY;
        this.minX = minX;
        this.minY = minY;
        this.ignoredCoordinates = ignored;
    }

    public HashMap<String, Float>[][] interpolate() {
        return featuresCache;
    }
}
