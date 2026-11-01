/** Formatting helpers for displaying GPS events */
public final class GpsFormat {
  private GpsFormat() {}

  
  public static String eventString(GpsEvent event) {
    return event.name + "," + event.latitude + "," + event.longitude + "," + event.altitude;
  }
}