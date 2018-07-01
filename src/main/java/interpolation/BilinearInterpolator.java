package interpolation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;


public class BilinearInterpolator extends Interpolator {

    private final BlockingQueue<String> queue;
    private Thread[] consumers;
    private final String QUEUE_END = "END_OF_QUEUE";

    public BilinearInterpolator(HashMap<String, Float>[][] featuresCache, ArrayList<String> allFeatures,
                                int maxX, int maxY, int minX, int minY) {
        super(featuresCache, allFeatures, maxX, maxY, minX, minY);


        queue = new LinkedBlockingDeque<String>();

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

                            updateFeaturesCache(ints);
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

        for(int r = minY; r < maxY; r++) {
            for(int c = minX; c < maxX; c++) {
                boolean needsInt = false;
                if(featuresCache[c][r] != null) {
                    HashMap<String, Float> coordMap = featuresCache[c][r];
                    if(!coordMap.containsKey(feature)) {
                        needsInt = true;
                    }
                } else {
                    needsInt = true;
                }

                if(needsInt) {
                    ReturnValue r1 = getUpperValues(feature, r, c);
                    ReturnValue r2 = getLowerValues(feature, r, c);

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

    private synchronized void updateFeaturesCache(ArrayList<Int> ints) {
        for (Int inter : ints) {
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

    public HashMap<String, Float>[][] interpolate() {

        try {
            for (String feature : allFeatures) {
                queue.put(feature);
            }

            for(Thread consumer : consumers) {
                queue.put(QUEUE_END);
            }
        } catch (InterruptedException ignored) {
            return null;
        }



        try {
            for(Thread consumer : consumers) {
                consumer.start();
            }

            for(Thread consumer : consumers) {
                consumer.join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
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
            String coord = Integer.toString(c) + "_" + Integer.toString(row);
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
            String key = Integer.toString(col) + "_" + Integer.toString(r);
            if(featuresCache[col][r] != null) {
                HashMap<String, Float> coordMap = featuresCache[col][r];
                if(coordMap.containsKey(feature)) {
                    return new ReturnValue(coordMap.get(feature), col);
                }
            }
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
            String key = Integer.toString(col) + "_" + Integer.toString(r);
            if(featuresCache[col][r] != null) {
                HashMap<String, Float> coordMap = featuresCache[col][r];
                if(coordMap.containsKey(feature)) {
                    return new ReturnValue(coordMap.get(feature), col);
                }
            }
        }
        return null;
    }

    private ReturnValue getLowerValues(String feature, int r, int c) {
        for(int row = r + 1; row < maxY; row++) {
            String coord = Integer.toString(c) + "_" + Integer.toString(row);
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
