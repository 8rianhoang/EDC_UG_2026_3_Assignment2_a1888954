/** 
 * shows a single set of GpsCoordinates
 */
public class GpsEvent {

    public String name;         //  name of the GPS Tracker
    public double latitude;     //  Latitude of the GPS event as a value from -90.0 to +90.0
    public double longitude;    //  Longitude of the GPS event as a value from -180.0 to +180.0
    public double altitude;     //  Altitude of the GPS event in feet

    /** 
     * creates GpsEvent
     */
    public GpsEvent(String name, double latitude, double longitude, double altitude){
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
    }

    /** 
     * Returns  String object that represents this GpsEvent's value
     * @return a string representation of the value of this object
     */
    public String toString(){
        return this.name+" | lat:"+this.latitude+" lon:"+this.longitude+" alt:"+this.altitude;
    }

} 
