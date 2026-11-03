import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GpsMath_Test {
  private static final double EPS = 1e-6;

  @Test
  void feetToMetres_convertsUsingExactFactor() {
    assertEquals(30.48, GpsMath.feetToMetres(100), EPS);
    assertEquals(0.0, GpsMath.feetToMetres(0), EPS);
    assertEquals(-0.3048, GpsMath.feetToMetres(-1), EPS);
  }

  @Test
  void distance_samePointIsZero() {
    TrackPoint p = new TrackPoint(39.9, 116.3, 50);
    assertEquals(0.0, GpsMath.distanceMetres(p, p), EPS);
  }

  @Test
  void distance_latitudeOnly() {
    TrackPoint a = new TrackPoint(39.000, 116.0, 0);
    TrackPoint b = new TrackPoint(39.001, 116.0, 0);
    assertEquals(111.32, GpsMath.distanceMetres(a, b), 1e-6);
  }

  @Test
  void distance_longitudeIsScaledByCosineOfLatitude() {
    TrackPoint a = new TrackPoint(60.0, 10.000, 0);
    TrackPoint b = new TrackPoint(60.0, 10.001, 0);
    // cos(60 degrees) = 0.5, so a degree of longitude is half as long as at the equator.
    assertEquals(55.66, GpsMath.distanceMetres(a, b), 1e-6);
  }

  @Test
  void distance_altitudeOnly() {
    TrackPoint a = new TrackPoint(0, 0, 0);
    TrackPoint b = new TrackPoint(0, 0, 12.5);
    assertEquals(12.5, GpsMath.distanceMetres(a, b), EPS);
  }

  @Test
  void distance_combinesHorizontalAndVertical() {
    // 3-4-5 triangle: 3 m north-ish, 4 m up.
    double threeMetresInDegrees = 3.0 / GpsMath.METRES_PER_DEGREE;
    TrackPoint a = new TrackPoint(0, 0, 0);
    TrackPoint b = new TrackPoint(threeMetresInDegrees, 0, 4);
    assertEquals(5.0, GpsMath.distanceMetres(a, b), 1e-6);
  }

  @Test
  void distance_isSymmetric() {
    TrackPoint a = new TrackPoint(39.1, 116.1, 10);
    TrackPoint b = new TrackPoint(39.2, 116.3, 70);
    assertEquals(GpsMath.distanceMetres(a, b), GpsMath.distanceMetres(b, a), EPS);
    assertTrue(GpsMath.distanceMetres(a, b) > 0);
  }
}
