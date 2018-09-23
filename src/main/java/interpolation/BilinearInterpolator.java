package interpolation;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class BilinearInterpolator extends Interpolator {

    private final BlockingQueue<String> queue;
    private Thread[] consumers;
    private final String QUEUE_END = "END_OF_QUEUE";
    public final static Object syncObject = new Object();


    ArrayList<ArrayList> featuresCacheUpdates = new ArrayList<ArrayList>();


    public BilinearInterpolator(HashMap<String, Float>[][] featuresCache, HashSet<String> allFeatures,
                                int maxX, int maxY, int minX, int minY, int[][] ignored) {
        super(featuresCache, allFeatures, maxX, maxY, minX, minY, ignored);

        queue = new LinkedBlockingDeque<String>();
    }

    private synchronized void addUpdates(ArrayList arrayList) {
        featuresCacheUpdates.add(arrayList);
    }

    private void createConsumers() {
        int cores = Runtime.getRuntime().availableProcessors();
        consumers = new Thread[cores];
        for(int i = 0; i < cores; i ++) {
            consumers[i] = new Thread() {
                @Override
                public void run() {
                    try {
                        while(true) {
                            String feature = queue.take();

                            if(feature.equals(QUEUE_END)) {
                                return;
                            }

                            ArrayList ints = getInts(feature);

                            if(ints.size() > 0) {
                                addUpdates(ints);
                            }
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            };
        }
    }

    private ArrayList<Int> getInts(String feature) {
        ArrayList<Int> ints = new ArrayList<Int>();

        for(int r = minY; r <= maxY; r++) {
            for(int c = minX; c <= maxX; c++) {
                boolean needsInt = true;
                //only time we don't want to interpolate is is the current coordinate already has a value for the current feature
                if(featuresCache[c][r] != null) {
                    HashMap<String, Float> coordMap = featuresCache[c][r];
                    if(coordMap.containsKey(feature)) {
                        needsInt = false;
                    }
                    if(ignoredCoordinates[c][r] == 1) {
                        needsInt = false;
                    }
                }

                if(needsInt) {
                    ReturnValue r1 = getUpperValues(feature, r, c);
                    ReturnValue r2 = getLowerValues(feature, r, c);

                    if(r1 == null){
                        r1 = extrapolateRowValues(feature, r - 1, c);
                    }

                    if(r2 == null) {
                        r2 = extrapolateRowValues(feature, r + 1, c);
                    }

                    if (r1 != null && r2 != null) {
                        Float inter = inter(r1.v1, r2.v1, r, r1.v2, r2.v2);
                        if(inter > 0) {
                            ints.add(new Int(r, c, feature, inter));
                        }
                    }
                }
            }
        }
        return ints;
    }

    private void updateFeaturesCache(ArrayList<Int> ints) {
        for (Int inter : ints) {
            if(ignoredCoordinates[inter.c][inter.r] == 1) {
                continue;
            }
            HashMap<String, Float> coordMap;
            if (featuresCache[inter.c][inter.r] != null) {
                coordMap = featuresCache[inter.c][inter.r];
            } else {
                coordMap = new HashMap<String, Float>();
            }

            coordMap.put(inter.feature, inter.value);
            featuresCache[inter.c][inter.r] = coordMap;
        }
    }

    private void runConsumers() throws InterruptedException {
        for (String feature : allFeatures) {
            queue.put(feature);
        }

        for(Thread consumer : consumers) {
            queue.put(QUEUE_END);
        }

        for(Thread consumer : consumers) {
            consumer.start();
        }

        for(Thread consumer : consumers) {
            consumer.join();
        }
    }

    public HashMap<String, Float>[][] interpolate() {
        try {
            createConsumers();
            runConsumers();
        } catch (InterruptedException ignored) {
            return null;
        }

        for(ArrayList updates : featuresCacheUpdates) {
            updateFeaturesCache(updates);
        }

        for(int x = 0; x < featuresCache.length; x++) {
            for(int y = 0; y < featuresCache[x].length; y++) {
                if(featuresCache[x][y] != null) {
                    HashMap<String, Float> coordMap = featuresCache[x][y];
                    String[] keys = coordMap.keySet().toArray(new String[0]);
                    for(String key : keys) {
                        String ap1 = key.substring(0, 17);
                        String ap2 = key.substring(17);
                        String newKey = ap2 + ap1;
                        Float values = coordMap.get(key);
                        coordMap.put(newKey, values);
                    }
                    featuresCache[x][y] = coordMap;
                }
            }
        }

        return featuresCache;
    }

    private float inter(float y1, float y2, int x, int x1, int x2) {
        int x2mx = x2 - x;
        int x2mx1 = x2 - x1;
        int xmx1 = x - x1;

        float a = ((float)x2mx / (float)x2mx1);
        float l = (a * y1);
        float b = ((float)xmx1 / (float)x2mx1);
        float r = (b * y2);

        return l + r;
    }

    private ReturnValue getUpperValues(String feature, int r, int c) {
        if(r == 0) {
            r = 1;
        }
        for(int row = r - 1; row >= 0; row--) {
            if(featuresCache[c][row] != null) {
                HashMap<String, Float> coordMap = featuresCache[c][row];
                if(coordMap.containsKey(feature)){
                    return new ReturnValue(coordMap.get(feature), row);
                }
            }
            ReturnValue r1 = getLeft(feature, row, c);
            ReturnValue r2 = getRight(feature, row, c);

            if(r1 != null && r2 != null) {
                Float inter = inter(r1.v1, r1.v1, c, r1.v2, r2.v2);
                return new ReturnValue(inter, row);
            }
        }

        return null;
    }

    private ReturnValue getLeft(String feature, int r, int c, boolean noRight) {
        ReturnValue returnValue = getLeft(feature, r, c);
        if(returnValue != null) {
            return returnValue;
        }

        if(noRight) {
            return null;
        }

        return getRight(feature, r, c, true);
    }

    private ReturnValue getLeft(String feature, int r, int c) {
        for(int col = c - 1; col >= 0; col--) {
            if(featuresCache[col][r] != null) {
                HashMap<String, Float> coordMap = featuresCache[col][r];
                if(coordMap.containsKey(feature)) {
                    return new ReturnValue(coordMap.get(feature), col);
                }
            }
        }

        return extrapolateColValues(feature, r, c - 1);
    }


    private ThreadLocal<HashMap<String, float[]>> colValCache = new ThreadLocal<HashMap<String, float[]>>();

    private ReturnValue extrapolateColValues(String feature, int r, int c) {
        float m = 0.0f;
        float b = 0.0f;
        int xSum = 0;
        float ySum = 0;
        float xySum = 0;
        float x2sum = 0;
        int length = 0;
        for (int col = 0; col <= maxX; col++) {
            if (featuresCache[col][r] != null) {
                HashMap<String, Float> coordMap = featuresCache[col][r];
                if (coordMap.containsKey(feature)) {
                    float y = coordMap.get(feature);
                    xSum += col;
                    ySum += y;
                    xySum += (col * y);
                    x2sum += Math.pow(col, 2);
                    length++;
                }
            }
        }
        if (length > 0) {
            m = (float) ((length * xySum - xSum * ySum) / (length * x2sum - Math.pow(xSum, 2)));
            b = (float) ((ySum - m * xSum) / length);
        }

        float value = b + m * c;
        if (!Float.isNaN(value) && value > 0.0) {
            return new ReturnValue(value, c);
        }

        return null;
    }

    private ReturnValue getRight(String feature, int r, int c, boolean noLeft) {
        ReturnValue returnValue = getRight(feature, r, c);
        if(returnValue != null) {
            return returnValue;
        }

        if(noLeft) {
            return null;
        }

        return getLeft(feature, r, c, true);
    }

    private ReturnValue getRight(String feature, int r, int c) {
        for(int col = c + 1; col <= maxX; col++) {
            if(featuresCache[col][r] != null) {
                HashMap<String, Float> coordMap = featuresCache[col][r];
                if(coordMap.containsKey(feature)) {
                    return new ReturnValue(coordMap.get(feature), col);
                }
            }
        }

        return extrapolateColValues(feature, r, c + 1);
    }

    private ReturnValue extrapolateRowValues(String feature, int r, int c) {
        int xSum = 0;
        float ySum = 0;
        float xySum = 0;
        float x2sum = 0;
        int length = 0;
        for(int row = 0; row <= maxY; row++) {
            ReturnValue found = null;
            if(featuresCache[c][row] != null) {
                HashMap<String, Float> coordMap = featuresCache[c][row];
                if(coordMap.containsKey(feature)){
                    found = new ReturnValue(coordMap.get(feature), row);
                }
            }
            ReturnValue r1 = getLeft(feature, row, c);
            ReturnValue r2 = getRight(feature, row, c);
            if(r1 != null && r2 != null){
                Float inter = inter(r1.v1, r2.v1, c, r1.v2, r2.v2);
                found = new ReturnValue(inter, row);
            }

            if(found != null) {
                xSum += found.v2;
                ySum += found.v1;
                xySum += (found.v2 * found.v1);
                x2sum += Math.pow(found.v2, 2);
                length++;
            }
        }

        if(length > 0) {
            float m = (float) ((length * xySum - xSum * ySum) / (length * x2sum - Math.pow(xSum, 2)));
            float b = (float) ((ySum - m * xSum) / length);
            float value = b + m * r;
            if(!Float.isNaN(value) && value > 0.0) {
                return new ReturnValue(value, r);
            }
        }
        return null;
    }

    private ReturnValue getLowerValues(String feature, int r, int c) {
        for(int row = r + 1; row <= maxY; row++) {
            if(featuresCache[c][row] != null) {
                HashMap<String, Float> coordMap = featuresCache[c][row];
                if(coordMap.containsKey(feature)){
                    return new ReturnValue(coordMap.get(feature), row);
                }
            }
            ReturnValue r1 = getLeft(feature, row, c);
            ReturnValue r2 = getRight(feature, row, c);
            if(r1 != null && r2 != null){
                Float inter = inter(r1.v1, r2.v1, c, r1.v2, r2.v2);
                return new ReturnValue(inter, row);
            }
        }
        return null;
    }

    private class ReturnValue {
        final Float v1;
        final int v2;

        ReturnValue(Float v1, int v2) {
            this.v1 = v1;
            this.v2 = v2;
        }
    }

    private class Int {
        final String feature;
        final Float value;
        final int r;
        final int c;

        Int(int r, int c, String feature, Float value) {

            this.r = r;
            this.c = c;
            this.feature = feature;
            this.value = value;
        }
    }
}
