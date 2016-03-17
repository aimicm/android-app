package wave.caribe.dashboard.model;


import com.mapbox.mapboxsdk.geometry.LatLng;

/**
 * caribe-android-dashboard
 * Created by tchap on 15/03/16.
 */
public class Sensor {

    private final String uid;
    private final String name;
    private final LatLng coords;

    public Sensor(String uid, String name, LatLng coords) {
        this.uid = uid;
        this.name = name;
        this.coords = coords;
    }

    public LatLng getLatLng() {
        return this.coords;
    }

    public String getName() {
        return this.name;
    }

    public String getUid() {
        if (uid == null) {
            return "No sensor associated";
        } else {
            return this.uid;
        }
    }

    public boolean hasUid() {
        return this.uid != null;
    }
}
