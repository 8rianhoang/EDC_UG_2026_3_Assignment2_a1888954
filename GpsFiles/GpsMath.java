/** Pure helper functions for unit conversion and distance. */
public final class GpsMath {
  public static final double FEET_TO_METRES = 0.3048;
  public static final double METRES_PER_DEGREE = 111_320.0;

  private GpsMath() {}

  public static double feetToMetres(double feet) {
    return feet * FEET_TO_METRES;
  }

  /** 3D distance in metres between two events (flat-earth, altitude included). */
  public static double distanceMetres(GpsEvent a, GpsEvent b) {
    double meanLat = Math.toRadians((a.latitude + b.latitude) / 2.0);
    double dy = (b.latitude - a.latitude) * METRES_PER_DEGREE;
    double dx = (b.longitude - a.longitude) * METRES_PER_DEGREE * Math.cos(meanLat);
    double dz = feetToMetres(b.altitude - a.altitude);
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
  }
}