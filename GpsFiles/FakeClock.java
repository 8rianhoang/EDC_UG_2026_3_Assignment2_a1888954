import java.util.ArrayList;
import java.util.List;
import nz.sodium.Transaction;
import nz.sodium.time.Timer;
import nz.sodium.time.TimerSystem;
import nz.sodium.time.TimerSystemImpl;

/**
 * A manually controlled clock for tests, so time-based behaviour (the 3-second clear and the
 * 5-minute window)
 */
public final class FakeClock implements TimerSystemImpl<Long> {
  private static final class Pending implements Timer {
    final long at;
    final Runnable callback;
    boolean cancelled;

    Pending(long at, Runnable callback) {
      this.at = at;
      this.callback = callback;
    }

    @Override
    public void cancel() {
      cancelled = true;
    }
  }

  private long now;
  private final List<Pending> pending = new ArrayList<Pending>();

  public FakeClock(long start) {
    this.now = start;
  }

  /** A Sodium TimerSystem driven by this clock */
  public TimerSystem<Long> system() {
    return new TimerSystem<Long>(this);
  }

  /** Moves  clock to the given time and lets Sodium run anything due */
  public void advanceTo(long time) {
    now = time;
    Transaction.runVoid(() -> {});
  }

  @Override
  public Timer setTimer(Long time, Runnable callback) {
    Pending timer = new Pending(time, callback);
    pending.add(timer);
    return timer;
  }

  @Override
  public void runTimersTo(Long time) {
    while (true) {
      Pending next = null;
      for (Pending p : pending) {
        if (!p.cancelled && p.at <= time && (next == null || p.at < next.at)) {
          next = p;
        }
      }
      if (next == null) {
        return;
      }
      pending.remove(next);
      next.callback.run();
    }
  }

  @Override
  public Long now() {
    return now;
  }
}
