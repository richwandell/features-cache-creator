import com.jsoniter.JsonIterator;
import com.jsoniter.any.Any;
import com.jsoniter.output.JsonStream;
import com.jsoniter.spi.JsonException;
import interpolation.BilinearInterpolator;


import java.sql.*;
import java.util.*;

class Db {

    private static final String LIMIT_XY_QUERY = "select " +
            "max(x) as maxx, " +
            "max(y) as maxy, " +
            "min(x) as minx, " +
            "min(y) as miny " +
            "from kalman_estimates where fp_id = ?;";

    private static final String KALMAN_QUERY = "select fp_id, x, y, ap_id, kalman from kalman_estimates where fp_id = ?;";

    private static final String FLOORPLAN_QUERY = "select layout_image from layout_images where id = ?;";

    private Connection conn;

    Db(String path) {
        try {
            // db parameters
            String url = "jdbc:sqlite:" + path;
            // create a connection to the database
            conn = DriverManager.getConnection(url);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private int[][] getIgnoredCoordinates(String fpId, int[] limitXY) {
        int[][] ignored = new int[limitXY[0] + 1][limitXY[1] + 1];
        try {
            PreparedStatement pstmt = conn.prepareStatement(FLOORPLAN_QUERY);
            pstmt.setString(1, fpId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String layoutString = rs.getString(1);

                try {
                    Any layout = JsonIterator.deserialize(layoutString);

                    ArrayList<Any> ignores = (ArrayList<Any>) layout.get("ignore").asList();
                    for(Any ignore : ignores) {
                        int x = ignore.get(0).toInt();
                        int y = ignore.get(1).toInt();
                        ignored[x][y] = 1;
                    }
                }catch(JsonException e) { }
            }
        }catch(Exception e) { }
        return ignored;
    }

    private int[] getLimitXY(String fpId) {
        try {
            PreparedStatement pstmt = conn.prepareStatement(LIMIT_XY_QUERY);
            pstmt.setString(1, fpId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                return new int[] {
                        rs.getInt(1),
                        rs.getInt(2),
                        rs.getInt(3),
                        rs.getInt(4)};
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String createFeaturesCache(String fpId, boolean interpolate) {

        try {

            int[] limitXy = getLimitXY(fpId);
            int[][] ignoredCoordinates = getIgnoredCoordinates(fpId, limitXy);


            PreparedStatement pstmt = conn.prepareStatement(KALMAN_QUERY);
            pstmt.setString(1, fpId);
            ResultSet rs = pstmt.executeQuery();

            ArrayList<Feature> features = new ArrayList<Feature>();

            while (rs.next()) {
                int x = rs.getInt(2);
                int y = rs.getInt(3);
                Feature f = new Feature(
                        rs.getString(1),
                        x,
                        y,
                        rs.getString(4),
                        rs.getFloat(5)
                );
                features.add(f);
            }

            HashMap<String, Float>[][] featuresCacheArray = new HashMap[limitXy[0] + 1][limitXy[1] + 1];

            HashSet<String> allFeatures = new HashSet<String>();
            int maxX = limitXy[0];
            int maxY = limitXy[1];
            int minX = limitXy[2];
            int minY = limitXy[3];
            for(Feature f1 : features) {
                for(Feature f2 : features) {
                    if(f1.x == f2.x && f1.y == f2.y) {
                        String feature = f1.apId + f2.apId;
                        String opposite = f2.apId + f1.apId;
                        Float value = Math.abs(f1.value - f2.value);

                        HashMap<String, Float> coordMap = null;
                        if(featuresCacheArray[f1.x][f1.y] != null) {
                            coordMap = featuresCacheArray[f1.x][f1.y];
                        } else {
                            coordMap = new HashMap<String, Float>();
                        }

                        coordMap.put(feature, value);
                        featuresCacheArray[f1.x][f1.y] = coordMap;
                        if(!allFeatures.contains(opposite)) {
                            allFeatures.add(feature);
                        }
                    }
                }
            }

            HashMap<String, HashMap<String, Float>> featuresCache;

            if(interpolate) {
//                final long startTime = System.currentTimeMillis();
                BilinearInterpolator bi = new BilinearInterpolator(
                        featuresCacheArray,
                        allFeatures,
                        maxX,
                        maxY,
                        minX,
                        minY,
                        ignoredCoordinates
                );
                featuresCacheArray = bi.interpolate();
//                long totalTime = System.currentTimeMillis() - startTime;
//                System.out.println(Long.toString(totalTime));
            }

            featuresCache = arrayToFeaturesCache(featuresCacheArray);


            String serialize = JsonStream.serialize(featuresCache);
            return serialize;

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private HashMap<String, HashMap<String, Float>> arrayToFeaturesCache(HashMap<String, Float>[][] fca) {
        HashMap<String, HashMap<String, Float>> featuresCache = new HashMap<String, HashMap<String, Float>>();

        for(int x = 0; x < fca.length; x++) {
            for(int y = 0; y < fca[x].length; y++) {
                if(fca[x][y] != null) {
                    String key = Integer.toString(x) + "_" + Integer.toString(y);
                    featuresCache.put(key, fca[x][y]);
                }
            }
        }
        return featuresCache;
    }
}