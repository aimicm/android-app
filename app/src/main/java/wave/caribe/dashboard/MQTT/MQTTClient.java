package wave.caribe.dashboard.MQTT;

/**
 * Caribe Wave Android App
 *
 * The MQTT Class manages the MQTT connection
 *
 * Created by tchap on 14/03/16.
 */
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.security.Timestamp;
import java.util.StringTokenizer;

import wave.caribe.dashboard.MainActivity;

public class MQTTClient implements MqttCallback {

    private static final String TAG = "CW:MQTT BROKER";

    int state = BEGIN;

    static final int BEGIN = 0;
    static final int CONNECTED = 1;
    static final int PUBLISHED = 2;
    static final int SUBSCRIBED = 3;
    static final int DISCONNECTED = 4;
    static final int FINISH = 5;
    static final int ERROR = 6;
    static final int DISCONNECT = 7;

    Object waiter = new Object();
    boolean donext = false;
    Throwable ex;

    MqttAsyncClient mClient;
    MqttConnectOptions connOpt;
    MQTTCallbackInterface mCallbackInterface;

    SharedPreferences sharedPref;

    // Used to generate a unique ID for the MQTT connection
    private String android_id;

    public MQTTClient(MainActivity activity) {
        sharedPref = PreferenceManager.getDefaultSharedPreferences(activity);

        // Create the unique ID
        android_id = "android_" + Settings.Secure.getString(activity.getContentResolver(),
                Settings.Secure.ANDROID_ID);
    }

    /**
     *
     * connectionLost
     * This callback is invoked upon losing the MQTT connection.
     *
     */
    @Override
    public void connectionLost(Throwable t) {
        Log.i(TAG, "Connection LOST, reconnecting : " + t);

            System.out.println("msg "+t.getMessage());
            System.out.println("loc "+t.getLocalizedMessage());
            System.out.println("cause "+t.getCause());
            System.out.println("excep "+t);
            t.printStackTrace();

        // Wait 5 secs before reconnecting
        try {
            Thread.sleep(5000);
            connect();
            subscribeToAll(mCallbackInterface);
        } catch (MqttException e) {
            e.printStackTrace();
        } catch (InterruptedException i) {
            i.printStackTrace();
        }
    }

    /**
     *
     * deliveryComplete
     * This callback is invoked when a message published by this client
     * is successfully received by the broker.
     *
     */
    @Override
    public void deliveryComplete(IMqttDeliveryToken token){
        Log.i(TAG, "Delivery complete");
    }

    /**
     *
     * messageArrived
     * This callback is invoked when a message is received on a subscribed topic.
     *
     */
    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        if(mCallbackInterface != null) {
            if (topic.equals("alert/general")) {
                Log.i(TAG, "General Alert : " + new String(message.getPayload()));
                mCallbackInterface.alert(new JSONObject(new String(message.getPayload())));
            } else if (topic.startsWith("measurement/")) {
                StringTokenizer tokens = new StringTokenizer(topic, "/");
                tokens.nextElement(); // skip first element
                String uid = tokens.nextElement().toString();
                Log.i(TAG, "New Measurement for " + uid + " : " + new String(message.getPayload()));
                mCallbackInterface.newMeasurement(uid, new JSONArray(new String(message.getPayload())));
            }
        }
    }

    public void subscribeToAll(MQTTCallbackInterface ci){

        // setup topic
        int subQoS = 1;

        try {
            if (sharedPref.getString("pref_data", "").length() > 1) {
                Log.i(TAG, "Subscribing to " + sharedPref.getString("pref_data", ""));
                //mClient.subscribe(sharedPref.getString("pref_data", ""), subQoS);
                subscribe(sharedPref.getString("pref_data", ""), subQoS);
            }
            if (sharedPref.getString("pref_alert", "").length() > 1) {
                Log.i(TAG, "Subscribing to " + sharedPref.getString("pref_alert", ""));
                //mClient.subscribe(sharedPref.getString("pref_alert", ""), subQoS);
                subscribe(sharedPref.getString("pref_alert", ""), subQoS);
            }
            mCallbackInterface = ci;
        } catch(MqttException me) {
            // Display full details of any exception that occurs
            System.out.println("reason "+me.getReasonCode());
            System.out.println("msg "+me.getMessage());
            System.out.println("loc "+me.getLocalizedMessage());
            System.out.println("cause "+me.getCause());
            System.out.println("excep "+me);
            me.printStackTrace();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     *
     *
     */
    public void connect() throws MqttException {
        connOpt = new MqttConnectOptions();

        connOpt.setCleanSession(true);
        connOpt.setKeepAliveInterval(3600);
        connOpt.setConnectionTimeout(3600);
        connOpt.setUserName(android_id);
        connOpt.setPassword(sharedPref.getString("pref_token", "").toCharArray());

        String tmpDir = System.getProperty("java.io.tmpdir");
        MqttDefaultFilePersistence dataStore = new MqttDefaultFilePersistence(tmpDir);

        // Connect to Broker
        mClient = new MqttAsyncClient(sharedPref.getString("pref_url", "") + ":" + sharedPref.getString("pref_port", "1883"), android_id + "_client", dataStore);
        mClient.setCallback(this);
        //mClient.connect(connOpt);
        //Log.i(TAG, "Connected to " + sharedPref.getString("pref_url", ""));

    }

    public void reconnect(MQTTCallbackInterface ci) {
        try {
            //disconnect();
            connect();
            subscribeToAll(ci);
        } catch (MqttException e) {
            Log.i(TAG, "Impossible to connect to the broker. Please check the settings and that you have an available internet connection, and retry.");
            e.printStackTrace();
        } catch (Exception e) {
            Log.i(TAG, "Problem connecting. Please check the settings, and retry.");
            e.printStackTrace();
        }
    }

    public void disconnect() {
        if (mClient != null) {
            try {
                mClient.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * Wait for a maximum amount of time for a state change event to occur
     * @param maxTTW  maximum time to wait in milliseconds
     * @throws MqttException
     */
    private void waitForStateChange(int maxTTW ) throws MqttException {
        synchronized (waiter) {
            if (!donext ) {
                try {
                    waiter.wait(maxTTW);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (ex != null) {
                    throw (MqttException)ex;
                }
            }
            donext = false;
        }
    }

    /**
     * Subscribe to a topic on an MQTT server
     * Once subscribed this method waits for the messages to arrive from the server
     * that match the subscription. It continues listening for messages until the enter key is
     * pressed.
     * @param topicName to subscribe to (can be wild carded)
     * @param qos the maximum quality of service to receive messages at for this subscription
     * @throws MqttException
     */
    public void subscribe(String topicName, int qos) throws Throwable {
        // Use a state machine to decide which step to do next. State change occurs
        // when a notification is received that an MQTT action has completed
        while (state != FINISH) {
            switch (state) {
                case BEGIN:
                    // Connect using a non-blocking connect
                    MqttConnector con = new MqttConnector();
                    con.doConnect();
                    break;
                case CONNECTED:
                    // Subscribe using a non-blocking subscribe
                    Subscriber sub = new Subscriber();
                    sub.doSubscribe(topicName, qos);
                    break;
                case SUBSCRIBED:
                    // Block until Enter is pressed allowing messages to arrive
                    try {
                        System.in.read();
                    } catch (IOException e) {
                        //If we can't read we'll just exit
                    }
                    state = DISCONNECT;
                    donext = true;
                    break;
                case DISCONNECT:
                    Disconnector disc = new Disconnector();
                    disc.doDisconnect();
                    break;
                case ERROR:
                    throw ex;
                case DISCONNECTED:
                    state = FINISH;
                    donext = true;
                    break;
            }

//    		if (state != FINISH && state != DISCONNECT) {
            waitForStateChange(10000);
        }
//    	}
    }


    /**
     * Connect in a non-blocking way and then sit back and wait to be
     * notified that the action has completed.
     */
    public class MqttConnector {

        public MqttConnector() {
        }

        public void doConnect() {
            // Connect to the server
            // Get a token and setup an asynchronous listener on the token which
            // will be notified once the connect completes
            Log.i(TAG, "Connecting to broker with client ID "+ mClient.getClientId());

            IMqttActionListener conListener = new IMqttActionListener() {
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.i(TAG, "Connected");
                    state = CONNECTED;
                    carryOn();
                }

                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    ex = exception;
                    state = ERROR;
                    Log.i(TAG, "connect failed" + exception);
                    carryOn();
                }

                public void carryOn() {
                    synchronized (waiter) {
                        donext=true;
                        waiter.notifyAll();
                    }
                }
            };

            try {
                // Connect using a non-blocking connect
                mClient.connect(connOpt,"Connect sample context", conListener);
            } catch (MqttException e) {
                // If though it is a non-blocking connect an exception can be
                // thrown if validation of parms fails or other checks such
                // as already connected fail.
                state = ERROR;
                donext = true;
                ex = e;
            }
        }
    }

    /**
     * Subscribe in a non-blocking way and then sit back and wait to be
     * notified that the action has completed.
     */
    public class Subscriber {
        public void doSubscribe(String topicName, int qos) {
            // Make a subscription
            // Get a token and setup an asynchronous listener on the token which
            // will be notified once the subscription is in place.
            Log.i(TAG, "Subscribing to topic \""+topicName+"\" qos "+qos);

            IMqttActionListener subListener = new IMqttActionListener() {
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.i(TAG, "Subscribe Completed");
                    state = SUBSCRIBED;
                    carryOn();
                }

                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    ex = exception;
                    state = ERROR;
                    Log.i(TAG, "Subscribe failed" + exception);
                    carryOn();
                }

                public void carryOn() {
                    synchronized (waiter) {
                        donext=true;
                        waiter.notifyAll();
                    }
                }
            };

            try {
                mClient.subscribe(topicName, qos, "Subscribe sample context", subListener);
            } catch (MqttException e) {
                state = ERROR;
                donext = true;
                ex = e;
            }
        }
    }

    /**
     * Disconnect in a non-blocking way and then sit back and wait to be
     * notified that the action has completed.
     */
    public class Disconnector {
        public void doDisconnect() {
            // Disconnect the client
            Log.i(TAG, "Disconnecting");

            IMqttActionListener discListener = new IMqttActionListener() {
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.i(TAG, "Disconnect Completed");
                    state = DISCONNECTED;
                    carryOn();
                }

                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    ex = exception;
                    state = ERROR;
                    Log.i(TAG, "Disconnect failed" + exception);
                    carryOn();
                }
                public void carryOn() {
                    synchronized (waiter) {
                        donext=true;
                        waiter.notifyAll();
                    }
                }
            };

            try {
                mClient.disconnect("Disconnect sample context", discListener);
            } catch (MqttException e) {
                state = ERROR;
                donext = true;
                ex = e;
            }
        }
    }
}