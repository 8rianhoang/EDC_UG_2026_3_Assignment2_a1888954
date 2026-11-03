import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TrackPoint_Test {
  @Test
  void from_convertsAltitudeFromFeetToMetres() {
    TrackPoint p = TrackPoint.from(new GpsEvent("Tracker1", 39.5, 116.5, 100.0));
    assertEquals(30.48, p.altitudeMetres, 1e-9);
  }

  @Test
  void from_keepsLatitudeAndLongitudeUnchanged() {
    TrackPoint p = TrackPoint.from(new GpsEvent("Tracker1", -34.92, 138.6, 0.0));
    assertEquals(-34.92, p.latitude, 0.0);
    assertEquals(138.6, p.longitude, 0.0);
  }
}
