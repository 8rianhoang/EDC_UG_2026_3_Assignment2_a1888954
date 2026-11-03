import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DistanceWindow_Test {
  private static final GpsRange ALL = GpsRange.unrestricted();
  private static final long FIVE_MIN = DistanceWindow.WINDOW_MS;
  // 0.001 degrees of latitude is 111.32 m.
  private static final TrackPoint P0 = new TrackPoint(39.000, 116.0, 0);
  private static final TrackPoint P1 = new TrackPoint(39.001, 116.0, 0);
  private static final TrackPoint P2 = new TrackPoint(39.002, 116.0, 0);

  @Test
  void empty_hasNoPointsAndZeroDistance() {
    assertEquals(0, DistanceWindow.empty().size());
    assertEquals(0L, DistanceWindow.empty().distanceMetres(ALL));
  }

  @Test
  void singlePoint_hasZeroDistance() {
    assertEquals(0L, DistanceWindow.empty().add(P0, 0).distanceMetres(ALL));
  }

  @Test
  void twoPoints_distanceIsRoundedUp() {
    DistanceWindow w = DistanceWindow.empty().add(P0, 0).add(P1, 1000);
    assertEquals(112L, w.distanceMetres(ALL));
  }

  @Test
  void threePoints_sumConsecutiveSegments() {
    DistanceWindow w = DistanceWindow.empty().add(P0, 0).add(P1, 1000).add(P2, 2000);
    assertEquals(223L, w.distanceMetres(ALL)); // 222.64 rounded up
  }

  @Test
  void exactWholeDistance_isNotRoundedUpByFloatingPointNoise() {
    DistanceWindow w =
        DistanceWindow.empty()
            .add(new TrackPoint(0, 0, 0), 0)
            .add(new TrackPoint(0, 0, 3.0), 1);
    assertEquals(3L, w.distanceMetres(ALL));
  }

  @Test
  void samePositionTwice_hasZeroDistance() {
    assertEquals(0L, DistanceWindow.empty().add(P0, 0).add(P0, 1000).distanceMetres(ALL));
  }

  @Test
  void altitudeChange_countsTowardsDistance() {
    DistanceWindow w =
        DistanceWindow.empty()
            .add(new TrackPoint(39, 116, 0), 0)
            .add(new TrackPoint(39, 116, 30.48), 1000);
    assertEquals(31L, w.distanceMetres(ALL));
  }

  @Test
  void pointsOlderThanFiveMinutes_areDroppedOnAdd() {
    DistanceWindow w = DistanceWindow.empty().add(P0, 0).add(P1, 1000);
    DistanceWindow later = w.add(P2, FIVE_MIN + 500);
    assertEquals(2, later.size()); // P0 dropped, P1 and P2 kept
    assertEquals(112L, later.distanceMetres(ALL));
  }

  @Test
  void prune_keepsPointsJustInsideTheWindow() {
    DistanceWindow w = DistanceWindow.empty().add(P0, 0).add(P1, 1000);
    assertEquals(2, w.prune(FIVE_MIN - 1).size());
  }

  @Test
  void prune_dropsPointExactlyFiveMinutesOld() {
    DistanceWindow w = DistanceWindow.empty().add(P0, 0).add(P1, 1000);
    assertEquals(1, w.prune(FIVE_MIN).size());
  }

  @Test
  void prune_dropsEverythingWhenAllPointsAreOld() {
    DistanceWindow w = DistanceWindow.empty().add(P0, 0).add(P1, 1000);
    DistanceWindow pruned = w.prune(FIVE_MIN + 2000);
    assertEquals(0, pruned.size());
    assertEquals(0L, pruned.distanceMetres(ALL));
  }

  @Test
  void prune_leavesOriginalWindowUnchanged() {
    DistanceWindow w = DistanceWindow.empty().add(P0, 0).add(P1, 1000);
    w.prune(FIVE_MIN + 2000);
    assertEquals(2, w.size());
  }

  @Test
  void range_pointsOutsideAreSkippedButNeighboursAreStillMeasured() {
    TrackPoint far = new TrackPoint(10, 10, 0);
    DistanceWindow w = DistanceWindow.empty().add(P0, 0).add(far, 1).add(P2, 2);
    GpsRange beijing = GpsRange.tryParse("39", "40", "116", "117").get();
    assertEquals(223L, w.distanceMetres(beijing)); // P0 to P2 directly
  }

  @Test
  void range_noPointsInsideGivesZero() {
    DistanceWindow w = DistanceWindow.empty().add(P0, 0).add(P1, 1000);
    GpsRange elsewhere = GpsRange.tryParse("50", "60", "", "").get();
    assertEquals(0L, w.distanceMetres(elsewhere));
  }

  @Test
  void range_onlyOnePointInsideGivesZero() {
    DistanceWindow w = DistanceWindow.empty().add(P0, 0).add(new TrackPoint(10, 10, 0), 1);
    GpsRange beijing = GpsRange.tryParse("39", "40", "116", "117").get();
    assertEquals(0L, w.distanceMetres(beijing));
  }
}
