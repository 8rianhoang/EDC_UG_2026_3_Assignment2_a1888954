import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class SimpleGps_Test {
  @Test
  void from_keepsTrackerNumberLatitudeAndLongitude() {
    SimpleGps g = SimpleGps.from(new GpsEvent("Tracker7", 30.5, 114.3, 999.0));
    assertEquals("7", g.trackerNumber);
    assertEquals(30.5, g.latitude, 0.0);
    assertEquals(114.3, g.longitude, 0.0);
  }

  @Test
  void from_handlesMultiDigitTrackerNumbers() {
    assertEquals("12", SimpleGps.from(new GpsEvent("Tracker12", 0, 0, 0)).trackerNumber);
  }

  @Test
  void simplifiedReading_hasNoAltitudeField() {
    for (Field f : SimpleGps.class.getDeclaredFields()) {
      assertFalse(f.getName().toLowerCase().contains("alt"), "unexpected field " + f.getName());
    }
  }
}
