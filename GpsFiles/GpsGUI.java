import nz.sodium.*;

public class GpsGUI {
  public static void main(String[] args) {
    GpsService serv = new GpsService();
    Stream<GpsEvent>[] streams = serv.getEventStreams();
    System.out.println("Trackers: " + streams.length);

    for (Stream<GpsEvent> s : streams) {
      // altitude removed with map
      Stream<String> simplified = s.map(e -> e.name + "," + e.latitude + "," + e.longitude);
      simplified.listen(System.out::println);
    }
  }
}