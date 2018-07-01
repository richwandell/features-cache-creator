package interpolation;

import java.util.ArrayList;
import java.util.HashMap;

public class Interpolator {

    protected HashMap<String, Float>[][] featuresCache;
    protected final ArrayList<String> allFeatures;
    protected final int maxX;
    protected final int maxY;
    protected final int minX;
    protected final int minY;

    public Interpolator(HashMap<String, Float>[][] featuresCache,
                        ArrayList<String> allFeatures, int maxX, int maxY, int minX, int minY) {

        this.featuresCache = featuresCache;
        this.allFeatures = allFeatures;
        this.maxX = maxX;
        this.maxY = maxY;
        this.minX = minX;
        this.minY = minY;
    }

    public HashMap<String, Float>[][] interpolate() {
        return featuresCache;
    }
}
