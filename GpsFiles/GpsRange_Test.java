import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class GpsRange_Test {
  private static GpsEvent at(double lat, double lon) {
    return new GpsEvent("Tracker1", lat, lon, 0);
  }

  private static GpsRange beijing() {
    return GpsRange.tryParse("39", "41", "116", "117").get();
  }

  @Test
  void unrestricted_containsEverything() {
    GpsRange r = GpsRange.unrestricted();
    assertTrue(r.contains(at(0, 0)));
    assertTrue(r.contains(at(90, 180)));
    assertTrue(r.contains(at(-90, -180)));
  }

  @Test
  void contains_insideAndOutsideLatitudeAndLongitude() {
    GpsRange r = beijing();
    assertTrue(r.contains(at(40, 116.5)));
    assertFalse(r.contains(at(38, 116.5)), "latitude too low");
    assertFalse(r.contains(at(42, 116.5)), "latitude too high");
    assertFalse(r.contains(at(40, 115)), "longitude too low");
    assertFalse(r.contains(at(40, 118)), "longitude too high");
  }

  @Test
  void contains_boundariesAreIncluded() {
    GpsRange r = beijing();
    assertTrue(r.contains(at(39, 116)));
    assertTrue(r.contains(at(41, 117)));
  }

  @Test
  void contains_positionOverloadMatchesEventVersion() {
    GpsRange r = beijing();
    assertTrue(r.contains(40.0, 116.5));
    assertFalse(r.contains(10.0, 116.5));
  }

  @Test
  void tryParse_validValues() {
    GpsRange r = GpsRange.tryParse("-35.5", "-34.5", "138", "139").get();
    assertEquals(-35.5, r.latMin, 0.0);
    assertEquals(-34.5, r.latMax, 0.0);
    assertEquals(138.0, r.lonMin, 0.0);
    assertEquals(139.0, r.lonMax, 0.0);
  }

  @Test
  void tryParse_blankFieldsMeanNoLimit() {
    GpsRange r = GpsRange.tryParse("", "", "", "").get();
    assertEquals(-90.0, r.latMin, 0.0);
    assertEquals(90.0, r.latMax, 0.0);
    assertEquals(-180.0, r.lonMin, 0.0);
    assertEquals(180.0, r.lonMax, 0.0);
  }

  @Test
  void tryParse_latitudeOnlyLeavesLongitudeOpen() {
    GpsRange r = GpsRange.tryParse("39", "41", "", "").get();
    assertTrue(r.contains(at(40, -100)));
    assertFalse(r.contains(at(10, 116)));
  }

  @Test
  void tryParse_longitudeOnlyLeavesLatitudeOpen() {
    GpsRange r = GpsRange.tryParse("", "", "116", "117").get();
    assertTrue(r.contains(at(-60, 116.5)));
    assertFalse(r.contains(at(40, 98)));
  }

  @Test
  void tryParse_whitespaceIsIgnored() {
    assertTrue(GpsRange.tryParse(" 39 ", " 41 ", " ", " ").isPresent());
  }

  @Test
  void tryParse_rejectsNonNumbers() {
    assertEquals(Optional.empty(), GpsRange.tryParse("abc", "", "", ""));
    assertEquals(Optional.empty(), GpsRange.tryParse("", "1,5", "", ""));
  }

  @Test
  void tryParse_rejectsNaNAndInfinity() {
    assertEquals(Optional.empty(), GpsRange.tryParse("NaN", "", "", ""));
    assertEquals(Optional.empty(), GpsRange.tryParse("", "Infinity", "", ""));
  }

  @Test
  void tryParse_rejectsOutOfRangeValues() {
    assertEquals(Optional.empty(), GpsRange.tryParse("-91", "", "", ""));
    assertEquals(Optional.empty(), GpsRange.tryParse("", "91", "", ""));
    assertEquals(Optional.empty(), GpsRange.tryParse("", "", "-181", ""));
    assertEquals(Optional.empty(), GpsRange.tryParse("", "", "", "181"));
  }

  @Test
  void tryParse_rejectsMinGreaterThanMax() {
    assertEquals(Optional.empty(), GpsRange.tryParse("41", "39", "", ""));
    assertEquals(Optional.empty(), GpsRange.tryParse("", "", "117", "116"));
  }

  @Test
  void tryParse_acceptsMinEqualToMax() {
    assertTrue(GpsRange.tryParse("40", "40", "116", "116").isPresent());
  }

  @Test
  void settingText_describesTheRange() {
    GpsRange r = beijing();
    assertEquals("39.0 to 41.0", r.latitudeText());
    assertEquals("116.0 to 117.0", r.longitudeText());
  }
}
