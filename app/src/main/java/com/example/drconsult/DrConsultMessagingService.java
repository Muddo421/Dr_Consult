package com.example.drconsult;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * DrConsultMessagingService
 *
 * Handles incoming FCM push notifications.
 *
 * ── SETUP STEPS ──
 * 1. Add to app/build.gradle.kts:
 *      implementation("com.google.firebase:firebase-messaging")
 *
 * 2. Add to AndroidManifest.xml inside <application>:
 *      <service
 *          android:name=".DrConsultMessagingService"
 *          android:exported="false">
 *          <intent-filter>
 *              <action android:name="com.google.firebase.MESSAGING_EVENT" />
 *          </intent-filter>
 *      </service>
 *
 * 3. MainActivity already calls:
 *      DrConsultMessagingService.subscribeToNotifications(currentUser.getUid());
 *    This saves the device token to Firestore so notifications can be sent.
 */
public class DrConsultMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";
    private static final String CHANNEL_ID   = "drconsult_notifications";
    private static final String CHANNEL_NAME = "DrConsult Alerts";

    /**
     * Called when a push notification arrives while the app is in the foreground.
     * When the app is in the background, Android shows the notification automatically.
     */
    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "Message received from: " + remoteMessage.getFrom());

        String title = "DrConsult";
        String body  = "You have a new notification.";
        String type  = null;

        // Extract from notification payload
        if (remoteMessage.getNotification() != null) {
            if (remoteMessage.getNotification().getTitle() != null)
                title = remoteMessage.getNotification().getTitle();
            if (remoteMessage.getNotification().getBody() != null)
                body = remoteMessage.getNotification().getBody();
        }

        // Extract from data payload (overrides notification payload if present)
        if (remoteMessage.getData().containsKey("title"))
            title = remoteMessage.getData().get("title");
        if (remoteMessage.getData().containsKey("body"))
            body = remoteMessage.getData().get("body");
        if (remoteMessage.getData().containsKey("type"))
            type = remoteMessage.getData().get("type");

        showNotification(title, body, type);
    }

    /**
     * Called when the FCM token is refreshed.
     * Saves the new token to Firestore so the backend can send targeted notifications.
     */
    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "FCM token refreshed");
        saveTokenToFirestore(token);
    }

    private void showNotification(String title, String body, String type) {
        createNotificationChannel();

        // Decide which screen opens when the notification is tapped
        Intent intent;
        if ("message".equals(type)) {
            intent = new Intent(this, MessagesActivity.class);
        } else if ("appointment".equals(type)) {
            intent = new Intent(this, AppointmentsActivity.class);
        } else {
            intent = new Intent(this, MainActivity.class);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            // Use unique ID so multiple notifications don't overwrite each other
            manager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Appointment and message alerts");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void saveTokenToFirestore(String token) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .update("fcmToken", token)
                .addOnFailureListener(e -> Log.w(TAG, "Could not save FCM token", e));
    }

    // ── Static helper — called from MainActivity after login ──────────────────

    /**
     * Fetches the current FCM token and saves it to Firestore under the user's document.
     * Call this from MainActivity.onCreate() after confirming user is logged in.
     *
     * Example:
     *   DrConsultMessagingService.subscribeToNotifications(currentUser.getUid());
     */
    public static void subscribeToNotifications(String userId) {
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {
                    Log.d(TAG, "FCM Token obtained");
                    if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                        FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(userId)
                                .update("fcmToken", token)
                                .addOnFailureListener(e ->
                                        Log.w(TAG, "Token save failed", e));
                    }
                })
                .addOnFailureListener(e -> Log.w(TAG, "Could not get FCM token", e));
    }
}