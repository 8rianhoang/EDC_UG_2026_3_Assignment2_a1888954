import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.Arrays;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.UnaryOperator;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import nz.sodium.Cell;
import nz.sodium.Stream;
import nz.sodium.StreamSink;
import nz.sodium.Unit;
import nz.sodium.time.MillisecondsTimerSystem;
import nz.sodium.time.TimerSystem;
import swidgets.SButton;
import swidgets.SLabel;
import swidgets.STextField;

/** GUI showing GPS data for each tracker, built with Sodium FRP and Swidgets */
public class GpsGUI {
  private static final long CLEAR_DELAY_MS = 3000;
  private static final int WIDE_LABEL_WIDTH = 270;
  private static final long TICK_MS = 1000;

  /** The rangefilter control panel and the cell holding the range currently in force */
  private static final class FilterPanel {
    final JPanel panel;
    final Cell<GpsRange> range;

    FilterPanel(JPanel panel, Cell<GpsRange> range) {
      this.panel = panel;
      this.range = range;
    }
  }

  public static void main(String[] args) {
    GpsService service = new GpsService();
    Stream<GpsEvent>[] streams = service.getEventStreams();
    SwingUtilities.invokeLater(() -> createAndShowGui(streams));
  }

  private static void createAndShowGui(Stream<GpsEvent>[] streams) {
    TimerSystem<Long> timer = new MillisecondsTimerSystem();
    FilterPanel filter = buildFilterPanel();

    // One tick per second lets the old points drop out of the 5-minute windows even when idle
    StreamSink<Unit> tick = new StreamSink<Unit>();
    new Timer(true)
        .schedule(
            new TimerTask() {
              @Override
              public void run() {
                tick.send(Unit.UNIT);
              }
            },
            TICK_MS,
            TICK_MS);

    JPanel trackerGrid = new JPanel(new GridLayout(0, 2, 8, 8));
    trackerGrid.setBorder(BorderFactory.createTitledBorder("Tracker displays"));
    JPanel distances = new JPanel(new GridLayout(0, 4, 8, 4));
    distances.setBorder(
        BorderFactory.createTitledBorder("Distance travelled, last 5 minutes (in range)"));
    for (int i = 0; i < streams.length; i++) {
      trackerGrid.add(buildTrackerPanel(i, streams[i], timer, filter.range));
      distances.add(new JLabel("Tracker " + i));
      distances.add(new SLabel(distanceText(streams[i], timer, tick, filter.range)));
    }

    // controls and the combined display sit beside the trackers so the window stays short
    JPanel side = new JPanel();
    side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
    side.add(filter.panel);
    side.add(buildCombinedPanel(streams, timer, filter.range));
    side.add(distances);

    JFrame frame = new JFrame("GPS Tracker Dashboard");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.add(trackerGrid, BorderLayout.CENTER);
    frame.add(side, BorderLayout.EAST);
    frame.pack();
    frame.setVisible(true);
  }

  /** One tracker: number, latitude, longitude, latest event and latest in-range event. */
  private static JPanel buildTrackerPanel(
      int index, Stream<GpsEvent> events, TimerSystem<Long> timer, Cell<GpsRange> range) {
    // Altitude is removed here with map.
    Stream<SimpleGps> simplified = events.map(SimpleGps::from);
    Cell<String> number = simplified.map(g -> g.trackerNumber).hold(String.valueOf(index));
    Cell<String> latitude = simplified.map(g -> String.valueOf(g.latitude)).hold("-");
    Cell<String> longitude = simplified.map(g -> String.valueOf(g.longitude)).hold("-");
    Cell<String> latestEvent = latestEventText(events, timer);

    // the latest in-range event, shown only while it is inside the current range. Because it is a lift over
    // the range cell it also updates straight away when the filter is changed.
    Cell<Optional<GpsEvent>> lastEvent = events.map(Optional::of).hold(Optional.empty());
    Cell<String> inRange =
        lastEvent.lift(
            range,
            (last, r) ->
                last.isPresent() && r.contains(last.get())
                    ? GpsFormat.eventString(last.get())
                    : "");

    JPanel panel = new JPanel(new GridLayout(5, 2, 4, 2));
    panel.setBorder(BorderFactory.createTitledBorder("Tracker " + index));
    panel.add(new JLabel("Tracker"));
    panel.add(new SLabel(number));
    panel.add(new JLabel("Latitude"));
    panel.add(new SLabel(latitude));
    panel.add(new JLabel("Longitude"));
    panel.add(new SLabel(longitude));
    panel.add(new JLabel("Latest event"));
    panel.add(wideLabel(latestEvent));
    panel.add(new JLabel("Latest in range"));
    panel.add(wideLabel(inRange));
    return panel;
  }

  /**
   * the total  distance (whole metres, rounded up) travelled by one tracker in the last 5 minutes,
   * counting only points inside the current range
   */
  private static Cell<String> distanceText(
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

  /** A label wide enough for a full event string (labels size themselves from empty text) */
  private static SLabel wideLabel(Cell<String> text) {
    SLabel label = new SLabel(text);
    label.setPreferredSize(new Dimension(WIDE_LABEL_WIDTH, label.getPreferredSize().height));
    return label;
  }

  /** Shows each event's text when it arrives and clears it 3 s later unless overwritten */
  private static Cell<String> latestEventText(Stream<GpsEvent> events, TimerSystem<Long> timer) {
    Stream<String> sText = events.map(GpsFormat::eventString);
    // Every new event pushes the alarm time back to (now + 3 s).
    Cell<Optional<Long>> clearAt =
        sText
            .snapshot(timer.time, (text, now) -> Optional.of(now + CLEAR_DELAY_MS))
            .hold(Optional.empty());
    Stream<String> sClear = timer.at(clearAt).map(t -> "");
    return sText.orElse(sClear).hold("");
  }

  /** Control panel: four input fields, a Set button and labels showing the current setting */
  private static FilterPanel buildFilterPanel() {
    STextField latMin = new STextField("", 10);
    STextField latMax = new STextField("", 10);
    STextField lonMin = new STextField("", 10);
    STextField lonMax = new STextField("", 10);
    SButton setButton = new SButton("Set filter");

    // What the fields currently say, as a range (empty if the text is not valid)
    Cell<Optional<GpsRange>> typed =
        latMin.text.lift(latMax.text, lonMin.text, lonMax.text, GpsRange::tryParse);

    // On each click, snapshot the typed range. Valid ranges replace the current one
    Stream<Optional<GpsRange>> sSet = setButton.sClicked.snapshot(typed);
    Cell<GpsRange> range =
        Stream.filterOptional(sSet).hold(GpsRange.unrestricted());
    Cell<String> status =
        sSet.map(r -> r.isPresent() ? "Filter applied" : "Invalid input - previous filter kept")
            .hold("No filter set (showing everything)");

    JPanel panel = new JPanel(new GridLayout(0, 2, 4, 4));
    panel.setBorder(BorderFactory.createTitledBorder("Range filter (blank = no limit)"));
    panel.add(new JLabel("Latitude min"));
    panel.add(latMin);
    panel.add(new JLabel("Latitude max"));
    panel.add(latMax);
    panel.add(new JLabel("Longitude min"));
    panel.add(lonMin);
    panel.add(new JLabel("Longitude max"));
    panel.add(lonMax);
    panel.add(setButton);
    panel.add(wideLabel(status));
    panel.add(new JLabel("Current latitude"));
    panel.add(new SLabel(range.map(GpsRange::latitudeText)));
    panel.add(new JLabel("Current longitude"));
    panel.add(new SLabel(range.map(GpsRange::longitudeText)));
    return new FilterPanel(panel, range);
  }

  /** One display for all trackers combined, showing only events inside the current range. */
  private static JPanel buildCombinedPanel(
      Stream<GpsEvent>[] streams, TimerSystem<Long> timer, Cell<GpsRange> range) {
    Stream<GpsEvent> all = Stream.orElse(Arrays.asList(streams));
    // snapshot reads the range as it was when the event arrived.
    Stream<GpsEvent> inRange =
        Stream.filterOptional(
            all.snapshot(
                range, (ev, r) -> r.contains(ev) ? Optional.of(ev) : Optional.<GpsEvent>empty()));

    JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));
    panel.setBorder(BorderFactory.createTitledBorder("All trackers, in range only"));
    panel.add(new JLabel("Latest in-range event"));
    panel.add(wideLabel(latestEventText(inRange, timer)));
    return panel;
  }
}