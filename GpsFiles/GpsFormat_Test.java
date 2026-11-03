import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GpsFormat_Test {
  @Test
  void eventString_isCommaDelimitedWithFourItems() {
    String s = GpsFormat.eventString(new GpsEvent("Tracker3", 39.97, 116.31, 120.0));
    assertEquals("Tracker3,39.97,116.31,120.0", s);
    assertEquals(4, s.split(",").length);
  }

  @Test
  void eventString_negativeCoordinates() {
    String s = GpsFormat.eventString(new GpsEvent("Tracker0", -34.9, -138.6, -5.5));
    assertEquals("Tracker0,-34.9,-138.6,-5.5", s);
  }
}
