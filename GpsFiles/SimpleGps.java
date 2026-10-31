/** A GPS reading with altitude removed. */
public final class SimpleGps {
  public final String trackerNumber;
  public final double latitude;
  public final double longitude;

  public SimpleGps(String trackerNumber, double latitude, double longitude) {
    this.trackerNumber = trackerNumber;
    this.latitude = latitude;
    this.longitude = longitude;
  }

  /** Drops the altitude and keeps only the tracker's number (e.g. "Tracker3" -> "3"). */
  public static SimpleGps from(GpsEvent event) {
    return new SimpleGps(event.name.replaceAll("\\D+", ""), event.latitude, event.longitude);
  }
}