package wave.caribe.dashboard;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.support.design.widget.FloatingActionButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.constants.MyBearingTracking;
import com.mapbox.mapboxsdk.constants.MyLocationTracking;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.views.MapView;

import wave.caribe.dashboard.MQTT.MQTTCallbackInterface;
import wave.caribe.dashboard.MQTT.MQTTClient;
import wave.caribe.dashboard.model.Sensor;
import wave.caribe.dashboard.services.AsyncHttpTask;
import wave.caribe.dashboard.services.RegistrationIntentService;


/**
 * Caribe Wave Android App
 *
 * Main entry point
 *
 * Created by tchap on 14/03/16.
 */
public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private static final String TAG = "CW:MAIN ACTIVITY";

    private MQTTClient mMQTTClient;
    private GoogleApiClient mGoogleApiClient;
    private Location mCurrentLocation;
    private LocationRequest mLocationRequest;

    private final int REQUEST_LOCATION = 1;
    private final String LOCATION_FILTER_KEY = "location.request.caribe.wave";

    private MapView mapView = null;
    private TextView msg;
    private LinearLayout msgbox;

    // Timers for display
    private Timer resetMarkerTimer = new Timer();
    private Timer resetAlertTimer = new Timer();

    private boolean alert_in_progress = false;
    private static final int WARNING_DISPLAY_TIME_IN_S = 5;
    private static final int ALERT_DISPLAY_TIME_IN_S = 120;

    private ArrayList<Sensor> sensors = new ArrayList<>();
    private ArrayList<Marker> listOfMarkers = new ArrayList<>();

    private static final int DEFAULT_ZOOM = 10;
    private float default_lat = 15.9369587f; // Marie Galante
    private float default_lon = -61.301064f;
    private final int LOCATION_INTERVAL = 2000;

    private Date mLastActive;

    private SharedPreferences sharedPref;

    // GCM
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    public static final String SENT_TOKEN_TO_SERVER = "sentTokenToServer";
    public static final String REGISTRATION_COMPLETE = "registrationComplete";

    // Do we have location permissions or not ?
    public boolean isLocationPermissionGranted() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    public void askPermissionForLocation() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                REQUEST_LOCATION);
    }

    public boolean isLocationServiceEnabled() {
        LocationManager locationManager = null;
        boolean gps_enabled= false,network_enabled = false;

        if(locationManager ==null)
            locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        try{
            gps_enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        }catch(Exception ex){
            //do nothing...
        }

        try{
            network_enabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        }catch(Exception ex){
            //do nothing...
        }

        return gps_enabled || network_enabled;

    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Force values (we hide the preference screen for now)
        PreferenceManager.setDefaultValues(this, R.xml.preferences, true);

        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        // Request fine location (Android > 6.0)
        if (!isLocationPermissionGranted()) {
            Log.i(TAG, "Asking for permissions (Location)");
            askPermissionForLocation();
        }

        // Check if we have Play Services for GCM
        if (checkPlayServices()) {
            Log.i(TAG, "Play Services are OK");
            // Start IntentService to register this application with GCM.
            Intent intent = new Intent(this, RegistrationIntentService.class);
            startService(intent);
        } else {
            Log.i(TAG, "Play Services are NOT ok");
        }

        // Instantiate map
        Log.i(TAG, "Instantiating map");
        mapView = (MapView) findViewById(R.id.map);
        mapView.setAccessToken(getString(R.string.mapbox_id));

        mapView.setStyleUrl("mapbox://styles/anthill/ciltunikq00eya2kq6s5nk85k");
        mapView.setCenterCoordinate(new LatLng(default_lat, default_lon));
        mapView.setZoomLevel(DEFAULT_ZOOM);
        mapView.setRotateEnabled(false);
        mapView.onCreate(savedInstanceState);

        FloatingActionButton mLocationButton = (FloatingActionButton) findViewById(R.id.location);

        if (isLocationPermissionGranted() && isLocationServiceEnabled()) {
            //noinspection ResourceType
            mapView.setMyLocationEnabled(true);
            //noinspection ResourceType
            mapView.setMyLocationTrackingMode(MyLocationTracking.TRACKING_NONE);
            //noinspection ResourceType
            mapView.setMyBearingTrackingMode(MyBearingTracking.GPS);


            mapView.setOnMyLocationChangeListener(new MapView.OnMyLocationChangeListener() {
                @Override
                public void onMyLocationChange(@Nullable Location location) {
                    if (location != null) {
                        CameraPosition cameraPosition = new CameraPosition.Builder()
                                .target(new LatLng(location))
                                .zoom(DEFAULT_ZOOM)
                                .build();
                        mapView.easeCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 1000, null);
                        mapView.setOnMyLocationChangeListener(null);
                    }
                }
            });

            mLocationButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Toggle GPS position updates
                    if (mCurrentLocation != null) {
                        CameraPosition cameraPosition = new CameraPosition.Builder()
                                .target(new LatLng(mCurrentLocation))
                                .build();
                        mapView.easeCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 1000, null);
                        mapView.setOnMyLocationChangeListener(null);
                    }
                }
            });
            mLocationButton.setVisibility(View.VISIBLE);

        } else if (isLocationPermissionGranted()) {
            mLocationButton.setVisibility(View.VISIBLE);
            mLocationButton.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.text)));
            mLocationButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(getApplicationContext(), R.string.error_location, Toast.LENGTH_LONG).show();
                }
            });
        }

        // Msg Box
        msg = (TextView) findViewById(R.id.msg);
        msgbox = (LinearLayout) findViewById(R.id.msgbox);
        msg.setText(R.string.no_event);

        // Build the Google API client for location requests
        Log.i(TAG, "Building Google API Client");
        buildGoogleApiClient();

        // Retrieve sensor list
        Log.i(TAG, "Retrieving Sensor list");
        getSensorList();

        // Build the MQTT Client
        Log.i(TAG, "Creating MQTT Client");
        mMQTTClient = new MQTTClient(this);

        Log.i(TAG, "-------------- DONE --------------");

    }

    private void getSensorList()
    {

        String api_url = sharedPref.getString("pref_sensor_api", "");

        if (api_url.equals("")) {
            Log.i(TAG, "Bad sensor list URL");
        } else {
            AsyncHttpTask task = new AsyncHttpTask(new AsyncHttpTask.TaskListener() {
                @Override
                public void onFinished(JSONArray result) {
                    // Translate to sensor ArrayList
                    Sensor current;
                    ArrayList<LatLng> coordinates = new ArrayList<>();

                    for (int i = 0; i < result.length(); i++) {
                        try {

                            JSONObject place = result.getJSONObject(i);
                            JSONArray sensor_uids = place.getJSONArray("sensor_uids");
                            LatLng c = new LatLng(Double.parseDouble(place.getString("lat")), Double.parseDouble(place.getString("lon")));

                            if (sensor_uids.length() > 0 && !sensor_uids.get(0).toString().equals("null")) {
                                current = new Sensor(sensor_uids.get(0).toString(), place.getString("name"), c);
                            } else {
                                current = new Sensor(null,place.getString("name"), c);
                            }
                            coordinates.add(c);
                            sensors.add(current);

                        } catch(JSONException e) {}
                    }

                    LatLng[] arr = coordinates.toArray(new LatLng[coordinates.size()]);
                    mapView.setVisibleCoordinateBounds(arr, new RectF(100, 100, 100, 100), true);

                    resetStillMarkers();
                }
            });
            task.execute(api_url);
        }

    }

    private void resetStillMarkers()
    {

        Log.i(TAG, "Resetting markers.");

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mapView.removeAllAnnotations();
                listOfMarkers.clear();
                msg.setText(R.string.no_event);
                msg.setTextColor(getResources().getColor(R.color.black));
                msgbox.setBackgroundColor(getResources().getColor(R.color.safe));
            }
        });
        
        IconFactory mIconFactory = IconFactory.getInstance(this);
        Drawable mIconDrawableDisabled = ContextCompat.getDrawable(this, R.drawable.map_grey);
        Icon disabled_icon = mIconFactory.fromDrawable(mIconDrawableDisabled);

        Drawable mIconDrawableActive = ContextCompat.getDrawable(this, R.drawable.map_green);
        Icon active_icon = mIconFactory.fromDrawable(mIconDrawableActive);

        for (int i = 0; i < sensors.size(); i++)
        {
            listOfMarkers.add(
                mapView.addMarker(new MarkerOptions()
                        .position(sensors.get(i).getLatLng())
                        .title(sensors.get(i).getName())
                        .icon(sensors.get(i).hasUid() ? active_icon : disabled_icon)
                        .snippet(sensors.get(i).getUid())));
        }

    }

    private synchronized void buildGoogleApiClient()
    {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();

        if (isLocationPermissionGranted()) {
            createLocationRequest();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {

        switch (item.getItemId()) {
            case R.id.action_retry:
                Toast.makeText(getApplicationContext(), R.string.msg_reconnecting, Toast.LENGTH_LONG).show();

                // Retrieve sensor list
                Log.i(TAG, "Retrieving Sensor list");
                getSensorList();
                Log.i(TAG, "Reconnecting MQTT");
                reconnect();
                return true;
            case R.id.action_settings:
                getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new SettingsFragment())
                    .addToBackStack("settings")
                    .commit();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void reconnect()
    {

        // In case of reconnection, make sure that everything comes
        // back to normal before reconnecting
        resetStillMarkers();

        HandlerThread handlerThread = new HandlerThread("MQTTHandler");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());
        Runnable r = new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Connecting MQTT Client");
                mMQTTClient.reconnect(new MQTTCallbackInterface() {
                    public void newMeasurement(String uid, JSONArray measurement) {
                        updateMap(uid, measurement);
                    }
                    public void alert(JSONObject alert) {
                        showAlert(alert);
                    }
                });
            }
        };

        handler.post(r);

    }

    public void updateMap(String uid, JSONArray measurement)
    {
        if (alert_in_progress) {
            // We don't want to interfere with a general alert
            return;
        }

        // A new event is coming
        resetMarkerTimer.cancel();

        updateLastActive();

        final ArrayList<String> places = new ArrayList<>();

        IconFactory mIconFactory = IconFactory.getInstance(this);
        Drawable mIconDrawableDanger = ContextCompat.getDrawable(this, R.drawable.map_yellow);
        Icon warning_icon = mIconFactory.fromDrawable(mIconDrawableDanger);

        for (final Marker marker : listOfMarkers) {
            if (marker.getSnippet().equals(uid)) {
                places.add(marker.getTitle());
                marker.setIcon(warning_icon);

                mapView.removeMarker(marker);
                /*
                listOfMarkers.set(listOfMarkers.indexOf(marker), mapView.addMarker(new MarkerOptions()
                        .position(marker.getPosition())
                        .title(marker.getTitle())
                        .icon(marker.getIcon())
                        .snippet(marker.getSnippet())));*/
                mapView.addMarker(new MarkerOptions()
                        .position(marker.getPosition())
                        .title(marker.getTitle())
                        .icon(marker.getIcon())
                        .snippet(marker.getSnippet()));

            }
        }

        // Update msg box
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                msg.setText(String.format(getString(R.string.measurement_event), TextUtils.join(", ", places)));
                msgbox.setBackgroundColor(getResources().getColor(R.color.warning));
            }
        });

        // Run for 2 seconds
        resetMarkerTimer = new Timer();
        resetMarkerTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!alert_in_progress) {
                    resetStillMarkers();
                }
                resetMarkerTimer.cancel();
            }
        }, WARNING_DISPLAY_TIME_IN_S*1000);

    }

    public void showAlert(JSONObject alert)
    {
        // A new event is coming
        resetMarkerTimer.cancel();
        alert_in_progress = true;

        String tmp;
        JSONArray sensor_uids;
        List<String> uids_list = new ArrayList<>();
        updateLastActive();

        try {
            tmp = alert.get("message").toString();
        } catch(Exception e) {
            tmp = getString(R.string.general_alert);
        }

        try {
            sensor_uids = (JSONArray) alert.get("sensors_uids");
            for (int i = 0; i < sensor_uids.length();uids_list.add(sensor_uids.get(i++).toString()));
        } catch(Exception e) {
        }

        IconFactory mIconFactory = IconFactory.getInstance(this);
        Drawable mIconDrawableDanger = ContextCompat.getDrawable(this, R.drawable.map_red);
        Icon danger_icon = mIconFactory.fromDrawable(mIconDrawableDanger);

        for (final Marker marker : listOfMarkers) {
            if (uids_list.contains(marker.getSnippet())) {
                marker.setIcon(danger_icon);
                mapView.removeMarker(marker);
                mapView.addMarker(new MarkerOptions()
                        .position(marker.getPosition())
                        .title(marker.getTitle())
                        .icon(marker.getIcon())
                        .snippet(marker.getSnippet()));
            }
        }

        final String message = tmp;
        // Update msg box with alert
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                msg.setText(message);
                msg.setTextColor(getResources().getColor(R.color.white));
                msgbox.setBackgroundColor(getResources().getColor(R.color.danger));
            }
        });

        // Run for 2 seconds
        resetAlertTimer = new Timer();
        resetAlertTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                resetStillMarkers();
                resetAlertTimer.cancel();
                alert_in_progress = false;
            }
        }, ALERT_DISPLAY_TIME_IN_S*1000);
    }

    public void updateLastActive()
    {
        mLastActive = new Date();
        //Log.i(TAG, "Data received at " + mLastActive.toString());
    }

    /*
    * Manages the back button in fragments stacks
    * */
    @Override
    public void onBackPressed()
    {
        if (getFragmentManager().getBackStackEntryCount() > 0) {
            getFragmentManager().popBackStack();
            getFragmentManager().beginTransaction().commit();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();
        mapView.onResume();
        reconnect(); // Reconnect MQTT
        // Within {@code onPause()}, we pause location updates, but leave the
        // connection to GoogleApiClient intact.
        startLocationUpdates();

    }

    @Override
    public void onPause()
    {
        super.onPause();
        mapView.onPause();
        // Stop location updates to save battery, but don't disconnect the GoogleApiClient object.
        stopLocationUpdates();
        if (mMQTTClient != null) {
            try {
                mMQTTClient.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onStop()
    {
        super.onStop();
        mapView.onStop();
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
        super.onStop();
    }

    @Override
    protected void onStart()
    {
        super.onStart();
        mapView.onStart();
        if (mGoogleApiClient != null && !mGoogleApiClient.isConnected() && !mGoogleApiClient.isConnecting()) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults)
    {
        switch (requestCode) {
            case REQUEST_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay
                    createLocationRequest();
                    startLocationUpdates();
                }
            }
        }
    }

    private void createLocationRequest()
    {
        if (mLocationRequest == null) { // Do not recreate it
            mLocationRequest = new LocationRequest();
            mLocationRequest.setInterval(LOCATION_INTERVAL);
            mLocationRequest.setFastestInterval(3000);
            mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        }
    }

    private void stopLocationUpdates()
    {
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected() && isLocationPermissionGranted()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        }
    }

    private void startLocationUpdates()
    {
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected() && isLocationPermissionGranted()) {
            //noinspection ResourceType
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        }
    }
    @Override
    public void onLocationChanged(Location location)
    {
        // We can set the flag back to true since this method is only
        // called when we have a new "real" location
        mCurrentLocation = location;
        //Log.i("  MAIN LOCATION UPDATE", mCurrentLocation.toString());

        Intent intent = new Intent(LOCATION_FILTER_KEY);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    /**
     * Runs when a GoogleApiClient object successfully connects.
     */
    @Override
    public void onConnected(Bundle connectionHint)
    {

        // If the initial location was never previously requested, we use
        // FusedLocationApi.getLastLocation() to get it. If it was previously requested, we store
        // its value in the Bundle and check for it in onCreate()
        //
        // Because we cache the value of the initial location in the Bundle, it means that if the
        // user launches the activity,
        // moves to a new location, and then changes the device orientation, the original location
        // is displayed as the activity is re-created.
        if (mCurrentLocation == null && isLocationPermissionGranted()) {
            //noinspection ResourceType
            mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        }

        startLocationUpdates();
    }

    @Override
    public void onConnectionSuspended(int cause)
    {
        // The connection to Google Play services was lost for some reason. We call connect() to
        // attempt to re-establish the connection.
        if (mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult result)
    {
        // Refer to the javadoc for ConnectionResult to see what error codes might be returned in
        // onConnectionFailed.
    }

    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    private boolean checkPlayServices()
    {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            Log.i(TAG, "GCM Error : " + resultCode);
            if (apiAvailability.isUserResolvableError(resultCode)) {
                apiAvailability.getErrorDialog(this, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST)
                        .show();
            } else {
                Log.i(TAG, "GCM Error : This device is not supported.");
                finish();
            }
            return false;
        }
        return true;
    }
}
