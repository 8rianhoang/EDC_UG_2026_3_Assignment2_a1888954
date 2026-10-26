import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import nz.sodium.Cell;
import nz.sodium.Stream;
import nz.sodium.time.MillisecondsTimerSystem;
import nz.sodium.time.TimerSystem;
import swidgets.SLabel;

/** GUI showing simplified GPS data for each tracker, built with Sodium FRP and Swidgets. */
public class GpsGUI {
  private static final long CLEAR_DELAY_MS = 3000;

  public static void main(String[] args) {
    GpsService service = new GpsService();
    Stream<GpsEvent>[] streams = service.getEventStreams();
    SwingUtilities.invokeLater(() -> createAndShowGui(streams));
  }

  private static void createAndShowGui(Stream<GpsEvent>[] streams) {
    TimerSystem<Long> timer = new MillisecondsTimerSystem();

    JPanel trackerGrid = new JPanel(new GridLayout(0, 2, 8, 8));
    trackerGrid.setBorder(BorderFactory.createTitledBorder("Tracker displays"));
    for (int i = 0; i < streams.length; i++) {
      trackerGrid.add(buildTrackerPanel(i, streams[i], timer));
    }

    JFrame frame = new JFrame("GPS Tracker Dashboard");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.add(trackerGrid, BorderLayout.CENTER);
    frame.pack();
    frame.setVisible(true);
  }

  /** One tracker: number, latitude, longitude and the latest event (cleared after 3 s). */
  private static JPanel buildTrackerPanel(
      int index, Stream<GpsEvent> events, TimerSystem<Long> timer) {
    // Altitude is removed here with map.
    Stream<SimpleGps> simplified = events.map(SimpleGps::from);
    Cell<String> number = simplified.map(g -> g.trackerNumber).hold(String.valueOf(index));
    Cell<String> latitude = simplified.map(g -> String.valueOf(g.latitude)).hold("-");
    Cell<String> longitude = simplified.map(g -> String.valueOf(g.longitude)).hold("-");
    Cell<String> latestEvent = latestEventText(events, timer);

    JPanel panel = new JPanel(new GridLayout(4, 2, 4, 2));
    panel.setBorder(BorderFactory.createTitledBorder("Tracker " + index));
    panel.add(new JLabel("Tracker"));
    panel.add(new SLabel(number));
    panel.add(new JLabel("Latitude"));
    panel.add(new SLabel(latitude));
    panel.add(new JLabel("Longitude"));
    panel.add(new SLabel(longitude));
    panel.add(new JLabel("Latest event"));
    panel.add(new SLabel(latestEvent));
    return panel;
  }

  /** Shows each event's text when it arrives and clears it 3 s later unless overwritten. */
  private static Cell<String> latestEventText(
      Stream<GpsEvent> events, TimerSystem<Long> timer) {
    Stream<String> sText = events.map(GpsFormat::eventString);
    // Every new event pushes the alarm time back to (now + 3 s).
    Cell<Optional<Long>> clearAt =
        sText
            .snapshot(timer.time, (text, now) -> Optional.of(now + CLEAR_DELAY_MS))
            .hold(Optional.empty());
    Stream<String> sClear = timer.at(clearAt).map(t -> "");
    return sText.orElse(sClear).hold("");
  }
}