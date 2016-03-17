package wave.caribe.dashboard.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.gcm.GcmListenerService;

import java.util.Random;

import wave.caribe.dashboard.MainActivity;
import wave.caribe.dashboard.R;

/**
 * Caribe Wave Android App
 *
 * GCM Listener for notifications
 *
 * Created by tchap on 14/03/16.
 */

public class CWGcmListenerService extends GcmListenerService {

    private static final String TAG = "CW:GCM LISTENER SERVICE";

    private final Random rand = new Random();

    @Override
    public void onMessageReceived(String from, Bundle data) {
        String message = data.getString("default");
        Log.i(TAG, "GCM Message received : " + message);

        sendNotification(message);
    }

    /**
     * Create and show a simple notification containing the received GCM message.
     *
     * @param message GCM message received.
     */
    private void sendNotification(String message) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0 /* Request code */, intent,
                PendingIntent.FLAG_ONE_SHOT);

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentTitle(getString(R.string.alert_title) + " — " + android.text.format.DateFormat.format("dd/MM/yy hh:mm:ss", new java.util.Date()))
                .setContentText(message)
                .setColor(getResources().getColor(R.color.primary))
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        notificationManager.notify(rand.nextInt(), notificationBuilder.build());
    }
}