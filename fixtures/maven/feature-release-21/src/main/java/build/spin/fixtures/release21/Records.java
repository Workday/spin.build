package build.spin.fixtures.release21;

public class Records {

    public record Point(int x, int y) {
        public double distance() {
            return Math.sqrt((double) x * x + (double) y * y);
        }
    }
}
