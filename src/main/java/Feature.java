public class Feature {

    public final String fpId;
    public final int x;
    public final int y;
    public final String apId;
    public float value;

    public Feature(String fpId, int x, int y, String apId, float value) {
        this.fpId = fpId;
        this.x = x;
        this.y = y;
        this.apId = apId;
        this.value = value;
    }
}
