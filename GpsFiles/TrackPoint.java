/** A position with the altitude already converted from feet to metres*/
public final class TrackPoint {
  public final double latitude;
  public final double longitude;
  public final double altitudeMetres;

  public TrackPoint(double latitude, double longitude, double altitudeMetres) {
    this.latitude = latitude;
    this.longitude = longitude;
    this.altitudeMetres = altitudeMetres;
  }

  /** Converts a GpsEvent (altitude in feet) into a point with altitude in metres */
  public static TrackPoint from(GpsEvent event) {
    return new TrackPoint(
        event.latitude, event.longitude, GpsMath.feetToMetres(event.altitude));
  }
}