import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An immutable sliding window of one tracker's points from the last 5 minutes. Every operation
 * returns a new window, so it can be used as the state of a Sodium accum
 */
public final class DistanceWindow {
  public static final long WINDOW_MS = 5 * 60 * 1000L;
  /** Guards against floating-point noise (e.g. 3.0000000001) being rounded up to 4*/
  private static final double CEIL_TOLERANCE = 1e-9;

  private static final class Entry {
    final TrackPoint point;
    final long timeMs;

    Entry(TrackPoint point, long timeMs) {
      this.point = point;
      this.timeMs = timeMs;
    }
  }

  private final List<Entry> entries;

  private DistanceWindow(List<Entry> entries) {
    this.entries = Collections.unmodifiableList(entries);
  }

  public static DistanceWindow empty() {
    return new DistanceWindow(new ArrayList<Entry>());
  }

  /** Adds a point seen at nowMs and drops anything 5 minutes or older */
  public DistanceWindow add(TrackPoint point, long nowMs) {
    List<Entry> kept = keptAt(nowMs);
    kept.add(new Entry(point, nowMs));
    return new DistanceWindow(kept);
  }

  /** Drops anything 5 minutes or older as of nowMs. */
  public DistanceWindow prune(long nowMs) {
    return new DistanceWindow(keptAt(nowMs));
  }

  public int size() {
    return entries.size();
  }

  /**
   * the total  distance in whole metres (round up) travelled between consecutive points that are
   * inside raneg. The Points outside the range are skipped, so the distance is measured between
   * the in-range points on either side of them
   */
  public long distanceMetres(GpsRange range) {
    double total = 0.0;
    TrackPoint previous = null;
    for (Entry entry : entries) {
      TrackPoint point = entry.point;
      if (!range.contains(point.latitude, point.longitude)) {
        continue;
      }
      if (previous != null) {
        total += GpsMath.distanceMetres(previous, point);
      }
      previous = point;
    }
    return (long) Math.ceil(total - CEIL_TOLERANCE);
  }

  private List<Entry> keptAt(long nowMs) {
    List<Entry> kept = new ArrayList<Entry>();
    for (Entry entry : entries) {
      if (entry.timeMs > nowMs - WINDOW_MS) {
        kept.add(entry);
      }
    }
    return kept;
  }
}