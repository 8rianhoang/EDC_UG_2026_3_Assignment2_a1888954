import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GpsEvent_Test {
  @Test
  void constructor_storesAllFourFields() {
    GpsEvent e = new GpsEvent("Tracker2", 39.9, 116.3, 120.0);
    assertEquals("Tracker2", e.name);
    assertEquals(39.9, e.latitude, 0.0);
    assertEquals(116.3, e.longitude, 0.0);
    assertEquals(120.0, e.altitude, 0.0);
  }

  @Test
  void toString_describesTheEvent() {
    assertEquals(
        "Tracker2 | lat:39.9 lon:116.3 alt:120.0",
        new GpsEvent("Tracker2", 39.9, 116.3, 120.0).toString());
  }
}
