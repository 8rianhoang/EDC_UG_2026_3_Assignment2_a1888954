import java.util.Arrays;
import java.util.Optional;
import java.util.function.UnaryOperator;
import nz.sodium.Cell;
import nz.sodium.Stream;
import nz.sodium.Unit;
import nz.sodium.time.TimerSystem;

/**
 * The FRP side of the program: every transformation from GPS event streams to the values the GUI
 * displays. It has no Swing code, so it can be tested without opening a window
 */
public final class GpsLogic {
  /** How long the latest event stays on screen if nothing replaces it. */
  public static final long CLEAR_DELAY_MS = 3000;

  private GpsLogic() {}

  /** The tracker number shown in a tracker's panel (the index until an event arrives). */
  public static Cell<String> numberText(Stream<GpsEvent> events, int index) {
    // Altitude is removed here with map.
    return events.map(SimpleGps::from).map(g -> g.trackerNumber).hold(String.valueOf(index));
  }

  /** The latest latitude, with altitude removed ("-" until an event arrives). */
  public static Cell<String> latitudeText(Stream<GpsEvent> events) {
    return events.map(SimpleGps::from).map(g -> String.valueOf(g.latitude)).hold("-");
  }

  /** The latest longitude, with altitude removed ("-" until an event arrives). */
  public static Cell<String> longitudeText(Stream<GpsEvent> events) {
    return events.map(SimpleGps::from).map(g -> String.valueOf(g.longitude)).hold("-");
  }

  /** Shows each event's text when it arrives and clears it 3 s later unless overwritten. */
  public static Cell<String> latestEventText(Stream<GpsEvent> events, TimerSystem<Long> timer) {
    Stream<String> sText = events.map(GpsFormat::eventString);
    // Every new event pushes the alarm time back to (now + 3 s).
    Cell<Optional<Long>> clearAt =
        sText
            .snapshot(timer.time, (text, now) -> Optional.of(now + CLEAR_DELAY_MS))
            .hold(Optional.empty());
    Stream<String> sClear = timer.at(clearAt).map(t -> "");
    return sText.orElse(sClear).hold("");
  }

  /**
   * The tracker's latest event, shown only while it is inside the current range. Because it is a
   * lift over the range cell it also updates straight away when the filter is changed.
   */
  public static Cell<String> inRangeText(Stream<GpsEvent> events, Cell<GpsRange> range) {
    Cell<Optional<GpsEvent>> lastEvent = events.map(Optional::of).hold(Optional.empty());
    return lastEvent.lift(
        range,
        (last, r) ->
            last.isPresent() && r.contains(last.get())
                ? GpsFormat.eventString(last.get())
                : "");
  }

  /** All trackers combined into one stream that only carries events inside the current range */
  public static Stream<GpsEvent> combinedInRange(
      Stream<GpsEvent>[] streams, Cell<GpsRange> range) {
    Stream<GpsEvent> all = Stream.orElse(Arrays.asList(streams));
    // snapshot reads the range as it was when the event arrived.
    return Stream.filterOptional(
        all.snapshot(
            range, (ev, r) -> r.contains(ev) ? Optional.of(ev) : Optional.<GpsEvent>empty()));
  }

  /**
   * Each click of the Set button snapshots the four text fields as a range. The result is empty
   * when the text is not a valid range
   */
  public static Stream<Optional<GpsRange>> setRequests(
      Stream<Unit> sClicked,
      Cell<String> latMin,
      Cell<String> latMax,
      Cell<String> lonMin,
      Cell<String> lonMax) {
    Cell<Optional<GpsRange>> typed = latMin.lift(latMax, lonMin, lonMax, GpsRange::tryParse);
    return sClicked.snapshot(typed);
  }

  /** The range in force: starts unrestricted, and only valid requests replace it */
  public static Cell<GpsRange> currentRange(Stream<Optional<GpsRange>> sSet) {
    return Stream.filterOptional(sSet).hold(GpsRange.unrestricted());
  }

  /** A msg saying whether the last Set click was applied. */
  public static Cell<String> filterStatus(Stream<Optional<GpsRange>> sSet) {
    return sSet
        .map(r -> r.isPresent() ? "Filter applied" : "Invalid input - previous filter kept")
        .hold("No filter set (showing everything)");
  }

  /**
   * Total distance (whole metres, rounded up) travelled by one tracker in the last 5 minutes,
   * counting only points inside the current range, as text such as "112 m"
   */
  public static Cell<String> distanceText(
      Stream<GpsEvent> events, TimerSystem<Long> timer, Stream<Unit> tick, Cell<GpsRange> range) {
    // Feet to metres happens here, in a map.
    Stream<TrackPoint> points = events.map(TrackPoint::from);
    Stream<UnaryOperator<DistanceWindow>> sAdd =
        points.snapshot(timer.time, (point, now) -> addPoint(point, now));
    Stream<UnaryOperator<DistanceWindow>> sPrune =
        tick.snapshot(timer.time, (unit, now) -> pruneAt(now));
    Cell<DistanceWindow> window =
        sAdd.orElse(sPrune).accum(DistanceWindow.empty(), (change, w) -> change.apply(w));
    // Lifting over the range means the total also updates when the filter changes.
    return window.lift(range, (w, r) -> w.distanceMetres(r) + " m");
  }

  private static UnaryOperator<DistanceWindow> addPoint(TrackPoint point, long now) {
    return w -> w.add(point, now);
  }

  private static UnaryOperator<DistanceWindow> pruneAt(long now) {
    return w -> w.prune(now);
  }
}
