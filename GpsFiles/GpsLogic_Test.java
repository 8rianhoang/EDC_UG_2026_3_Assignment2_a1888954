import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import nz.sodium.Cell;
import nz.sodium.CellSink;
import nz.sodium.Stream;
import nz.sodium.StreamSink;
import nz.sodium.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests the FRP transformations with sinks and a fake clock (no GUI, no real waiting) */
class GpsLogic_Test {
  private FakeClock clock;
  private nz.sodium.time.TimerSystem<Long> timer;
  private StreamSink<GpsEvent> events;

  @BeforeEach
  void setUp() {
    clock = new FakeClock(0);
    timer = clock.system();
    events = new StreamSink<GpsEvent>();
  }

  private static GpsEvent event(String name, double lat, double lon, double alt) {
    return new GpsEvent(name, lat, lon, alt);
  }

  private static GpsRange range(String latMin, String latMax, String lonMin, String lonMax) {
    return GpsRange.tryParse(latMin, latMax, lonMin, lonMax).get();
  }

  // ---- Simplified tracker display (altitude removed) ----

  @Test
  void tracker_showsIndexAndDashesBeforeFirstEvent() {
    assertEquals("4", GpsLogic.numberText(events, 4).sample());
    assertEquals("-", GpsLogic.latitudeText(events).sample());
    assertEquals("-", GpsLogic.longitudeText(events).sample());
  }

  @Test
  void tracker_showsNumberLatitudeAndLongitudeWithoutAltitude() {
    Cell<String> number = GpsLogic.numberText(events, 4);
    Cell<String> lat = GpsLogic.latitudeText(events);
    Cell<String> lon = GpsLogic.longitudeText(events);
    events.send(event("Tracker4", 7.879058, 98.39548, 250.0));
    assertEquals("4", number.sample());
    assertEquals("7.879058", lat.sample());
    assertEquals("98.39548", lon.sample());
  }

  @Test
  void tracker_updatesWithEachNewEvent() {
    Cell<String> lat = GpsLogic.latitudeText(events);
    events.send(event("Tracker4", 1.0, 2.0, 0));
    events.send(event("Tracker4", 3.0, 4.0, 0));
    assertEquals("3.0", lat.sample());
  }

  // ---- Latest event, cleared after 3 seconds ----

  @Test
  void latestEvent_isEmptyBeforeAnyEvent() {
    assertEquals("", GpsLogic.latestEventText(events, timer).sample());
  }

  @Test
  void latestEvent_showsFourItemsThenClearsAfterThreeSeconds() {
    Cell<String> latest = GpsLogic.latestEventText(events, timer);
    events.send(event("Tracker1", 39.9, 116.3, 100.0));
    assertEquals("Tracker1,39.9,116.3,100.0", latest.sample());
    clock.advanceTo(2999);
    assertEquals("Tracker1,39.9,116.3,100.0", latest.sample());
    clock.advanceTo(3000);
    assertEquals("", latest.sample());
  }

  @Test
  void latestEvent_newEventReplacesTextAndRestartsTheThreeSeconds() {
    Cell<String> latest = GpsLogic.latestEventText(events, timer);
    events.send(event("Tracker1", 1, 1, 1));
    clock.advanceTo(2000);
    events.send(event("Tracker1", 2, 2, 2));
    assertEquals("Tracker1,2.0,2.0,2.0", latest.sample());
    clock.advanceTo(4999); // 2999 ms after the second event
    assertEquals("Tracker1,2.0,2.0,2.0", latest.sample());
    clock.advanceTo(5000);
    assertEquals("", latest.sample());
  }

  // ---- Per-tracker in-range display ----

  @Test
  void inRangeText_showsEventInsideRangeAndBlanksOutsideRange() {
    CellSink<GpsRange> range = new CellSink<GpsRange>(range("39", "41", "116", "117"));
    Cell<String> shown = GpsLogic.inRangeText(events, range);
    assertEquals("", shown.sample());
    events.send(event("Tracker1", 40, 116.5, 10));
    assertEquals("Tracker1,40.0,116.5,10.0", shown.sample());
    events.send(event("Tracker1", 7.8, 98.4, 10));
    assertEquals("", shown.sample());
  }

  @Test
  void inRangeText_updatesImmediatelyWhenTheRangeChanges() {
    CellSink<GpsRange> range = new CellSink<GpsRange>(GpsRange.unrestricted());
    Cell<String> shown = GpsLogic.inRangeText(events, range);
    events.send(event("Tracker1", 40, 116.5, 10));
    assertEquals("Tracker1,40.0,116.5,10.0", shown.sample());
    range.send(range("50", "60", "", ""));
    assertEquals("", shown.sample());
    range.send(GpsRange.unrestricted());
    assertEquals("Tracker1,40.0,116.5,10.0", shown.sample());
  }

  // ---- Combined display ----

  @SuppressWarnings({"unchecked", "rawtypes"})
  private Stream<GpsEvent>[] threeStreams(StreamSink<GpsEvent> a, StreamSink<GpsEvent> b,
      StreamSink<GpsEvent> c) {
    return (Stream<GpsEvent>[]) new Stream[] {a, b, c};
  }

  @Test
  void combined_mergesAllStreamsWhenUnrestricted() {
    StreamSink<GpsEvent> a = new StreamSink<GpsEvent>();
    StreamSink<GpsEvent> b = new StreamSink<GpsEvent>();
    StreamSink<GpsEvent> c = new StreamSink<GpsEvent>();
    CellSink<GpsRange> range = new CellSink<GpsRange>(GpsRange.unrestricted());
    List<String> seen = new ArrayList<String>();
    GpsLogic.combinedInRange(threeStreams(a, b, c), range).listen(e -> seen.add(e.name));
    a.send(event("Tracker0", 1, 1, 0));
    b.send(event("Tracker1", 2, 2, 0));
    c.send(event("Tracker2", 3, 3, 0));
    assertEquals(List.of("Tracker0", "Tracker1", "Tracker2"), seen);
  }

  @Test
  void combined_onlyPassesEventsInsideTheRange() {
    StreamSink<GpsEvent> a = new StreamSink<GpsEvent>();
    StreamSink<GpsEvent> b = new StreamSink<GpsEvent>();
    StreamSink<GpsEvent> c = new StreamSink<GpsEvent>();
    CellSink<GpsRange> range = new CellSink<GpsRange>(range("39", "41", "116", "117"));
    List<String> seen = new ArrayList<String>();
    GpsLogic.combinedInRange(threeStreams(a, b, c), range).listen(e -> seen.add(e.name));
    a.send(event("Tracker0", 40, 116.5, 0)); // inside
    b.send(event("Tracker1", 7.8, 98.4, 0)); // outside
    c.send(event("Tracker2", 40, 98.4, 0)); // latitude ok, longitude outside
    assertEquals(List.of("Tracker0"), seen);
  }

  @Test
  void combined_usesTheRangeInForceWhenTheEventArrives() {
    StreamSink<GpsEvent> a = new StreamSink<GpsEvent>();
    StreamSink<GpsEvent> b = new StreamSink<GpsEvent>();
    StreamSink<GpsEvent> c = new StreamSink<GpsEvent>();
    CellSink<GpsRange> range = new CellSink<GpsRange>(range("50", "60", "", ""));
    List<String> seen = new ArrayList<String>();
    GpsLogic.combinedInRange(threeStreams(a, b, c), range).listen(e -> seen.add(e.name));
    a.send(event("Tracker0", 40, 116.5, 0));
    assertEquals(List.of(), seen);
    range.send(range("39", "41", "", ""));
    a.send(event("Tracker0", 40, 116.5, 0));
    assertEquals(List.of("Tracker0"), seen);
  }

  // ---- Filter controls ----

  private static class Controls {
    final StreamSink<Unit> click = new StreamSink<Unit>();
    final CellSink<String> latMin = new CellSink<String>("");
    final CellSink<String> latMax = new CellSink<String>("");
    final CellSink<String> lonMin = new CellSink<String>("");
    final CellSink<String> lonMax = new CellSink<String>("");
    final Stream<Optional<GpsRange>> requests =
        GpsLogic.setRequests(click, latMin, latMax, lonMin, lonMax);
    final Cell<GpsRange> range = GpsLogic.currentRange(requests);
    final Cell<String> status = GpsLogic.filterStatus(requests);
  }

  @Test
  void filter_startsUnrestrictedWithNoStatus() {
    Controls c = new Controls();
    assertEquals("-90.0 to 90.0", c.range.sample().latitudeText());
    assertEquals("-180.0 to 180.0", c.range.sample().longitudeText());
    assertEquals("No filter set (showing everything)", c.status.sample());
  }

  @Test
  void filter_typingAloneDoesNothingUntilSetIsClicked() {
    Controls c = new Controls();
    c.latMin.send("39");
    c.latMax.send("41");
    assertEquals("-90.0 to 90.0", c.range.sample().latitudeText());
  }

  @Test
  void filter_clickingSetAppliesTheTypedRange() {
    Controls c = new Controls();
    c.latMin.send("39");
    c.latMax.send("41");
    c.lonMin.send("116");
    c.lonMax.send("117");
    c.click.send(Unit.UNIT);
    assertEquals("39.0 to 41.0", c.range.sample().latitudeText());
    assertEquals("116.0 to 117.0", c.range.sample().longitudeText());
    assertEquals("Filter applied", c.status.sample());
  }

  @Test
  void filter_invalidInputKeepsThePreviousRangeAndReportsIt() {
    Controls c = new Controls();
    c.latMin.send("39");
    c.latMax.send("41");
    c.click.send(Unit.UNIT);
    c.latMin.send("abc");
    c.click.send(Unit.UNIT);
    assertEquals("39.0 to 41.0", c.range.sample().latitudeText());
    assertEquals("Invalid input - previous filter kept", c.status.sample());
  }

  // ---- Distance ----

  private static final class Distance {
    final StreamSink<GpsEvent> events = new StreamSink<GpsEvent>();
    final StreamSink<Unit> tick = new StreamSink<Unit>();
    final CellSink<GpsRange> range;
    final Cell<String> text;

    Distance(nz.sodium.time.TimerSystem<Long> timer, GpsRange initial) {
      range = new CellSink<GpsRange>(initial);
      text = GpsLogic.distanceText(events, timer, tick, range);
    }
  }

  @Test
  void distance_startsAtZero() {
    assertEquals("0 m", new Distance(timer, GpsRange.unrestricted()).text.sample());
  }

  @Test
  void distance_oneEventIsStillZero() {
    Distance d = new Distance(timer, GpsRange.unrestricted());
    d.events.send(event("Tracker1", 39.000, 116.0, 0));
    assertEquals("0 m", d.text.sample());
  }

  @Test
  void distance_twoEventsGiveTheDistanceRoundedUpInMetres() {
    Distance d = new Distance(timer, GpsRange.unrestricted());
    d.events.send(event("Tracker1", 39.000, 116.0, 0));
    clock.advanceTo(1000);
    d.events.send(event("Tracker1", 39.001, 116.0, 0));
    assertEquals("112 m", d.text.sample());
  }

  @Test
  void distance_altitudeIsConvertedFromFeetToMetres() {
    Distance d = new Distance(timer, GpsRange.unrestricted());
    d.events.send(event("Tracker1", 39.0, 116.0, 0));
    d.events.send(event("Tracker1", 39.0, 116.0, 100)); // 100 ft = 30.48 m
    assertEquals("31 m", d.text.sample());
  }

  @Test
  void distance_fallsBackToZeroWhenPointsExpireAfterFiveMinutes() {
    Distance d = new Distance(timer, GpsRange.unrestricted());
    d.events.send(event("Tracker1", 39.000, 116.0, 0));
    clock.advanceTo(1000);
    d.events.send(event("Tracker1", 39.001, 116.0, 0));
    assertEquals("112 m", d.text.sample());
    clock.advanceTo(DistanceWindow.WINDOW_MS + 500); // first point is now older than 5 minutes
    d.tick.send(Unit.UNIT);
    assertEquals("0 m", d.text.sample());
  }

  @Test
  void distance_pointsInsideTheWindowAreStillCountedAfterATick() {
    Distance d = new Distance(timer, GpsRange.unrestricted());
    d.events.send(event("Tracker1", 39.000, 116.0, 0));
    clock.advanceTo(1000);
    d.events.send(event("Tracker1", 39.001, 116.0, 0));
    clock.advanceTo(60_000);
    d.tick.send(Unit.UNIT);
    assertEquals("112 m", d.text.sample());
  }

  @Test
  void distance_onlyCountsEventsInsideTheCurrentRange() {
    Distance d = new Distance(timer, range("39", "40", "116", "117"));
    d.events.send(event("Tracker1", 39.000, 116.0, 0));
    d.events.send(event("Tracker1", 10.0, 10.0, 0)); // outside, ignored
    d.events.send(event("Tracker1", 39.002, 116.0, 0));
    assertEquals("223 m", d.text.sample()); // straight from the first to the third
  }

  @Test
  void distance_updatesWhenTheFilterChanges() {
    Distance d = new Distance(timer, GpsRange.unrestricted());
    d.events.send(event("Tracker1", 39.000, 116.0, 0));
    d.events.send(event("Tracker1", 39.001, 116.0, 0));
    assertEquals("112 m", d.text.sample());
    d.range.send(range("50", "60", "", ""));
    assertEquals("0 m", d.text.sample());
    d.range.send(GpsRange.unrestricted());
    assertEquals("112 m", d.text.sample());
  }
}
