package __PACKAGE__;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * सर्वर से आए FCM (data-only) संदेश:
 *  incoming_call -> फ़ुल-स्क्रीन कॉल notification (✅ उठाएँ / ❌ काटें)
 *  missed_call   -> रिंग हटाकर "मिस्ड कॉल" notification
 *  new_message   -> मैसेज notification (टैप करने पर उसी की चैट खुलती है)
 */
public class GGMessagingService extends FirebaseMessagingService {

    static final String CH_CALLS = "gg_calls_v2";
    static final String CH_MESSAGES = "gg_messages_v1";
    static final String CH_MISSED = "gg_missed_v1";
    static final int NOTIF_CALL = 7001;
    static final String PREFS = "gg_prefs";
    static final long CALL_RING_MS = 45000;

    @Override
    public void onNewToken(@NonNull String token) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("fcm_token", token).apply();
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        Map<String, String> d = message.getData();
        String type = d.get("type");
        if (type == null) return;

        String from = orEmpty(d.get("from"));
        String to = orEmpty(d.get("to"));
        String callId = orEmpty(d.get("callId"));
        String title = d.get("title");
        String body = d.get("body");

        createChannels();

        switch (type) {
            case "incoming_call":
                if (!MainActivity.visible) showIncomingCall(from, to, callId, title, body);
                break;
            case "missed_call":
                NotificationManagerCompat.from(this).cancel(NOTIF_CALL);
                showSimple(CH_MISSED, from.hashCode() + 1, "missed_call", from,
                        title != null ? title : "मिस्ड कॉल", body != null ? body : from);
                break;
            case "new_message":
                if (!MainActivity.visible) {
                    showSimple(CH_MESSAGES, from.hashCode(), "new_message", from,
                            title != null ? title : from, body != null ? body : "नया संदेश");
                }
                break;
            default:
                break;
        }
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private int flags() {
        return PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    }

    private Intent launchIntent(String type, String action, String from, String callId) {
        return new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("gg_type", type)
                .putExtra("gg_action", action)
                .putExtra("gg_from", from)
                .putExtra("gg_callId", callId);
    }

    private boolean canNotify() {
        return Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    private void showIncomingCall(String from, String to, String callId, String title, String body) {
        if (!canNotify()) return;

        PendingIntent contentPI = PendingIntent.getActivity(this, 11,
                launchIntent("incoming_call", "", from, callId), flags());
        PendingIntent acceptPI = PendingIntent.getActivity(this, 12,
                launchIntent("incoming_call", "accept", from, callId), flags());

        Intent rej = new Intent(this, CallActionReceiver.class)
                .setAction("gg.REJECT_CALL")
                .putExtra("to", to)
                .putExtra("from", from)
                .putExtra("callId", callId);
        PendingIntent rejectPI = PendingIntent.getBroadcast(this, 13, rej, flags());

        Uri ring = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CH_CALLS)
                .setSmallIcon(android.R.drawable.sym_action_call)
                .setContentTitle(title != null ? title : "आने वाली कॉल")
                .setContentText(body != null ? body : from)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(true)
                .setTimeoutAfter(CALL_RING_MS)
                .setContentIntent(contentPI)
                .setFullScreenIntent(contentPI, true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "❌ काटें", rejectPI)
                .addAction(android.R.drawable.sym_action_call, "✅ उठाएँ", acceptPI)
                .setSound(ring)
                .setVibrate(new long[]{0, 800, 400, 800, 400, 800});

        Notification n = b.build();
        n.flags |= Notification.FLAG_INSISTENT; // कॉल खत्म होने तक रिंग दोहराता रहे
        NotificationManagerCompat.from(this).notify(NOTIF_CALL, n);
    }

    @SuppressLint("MissingPermission")
    private void showSimple(String channel, int id, String type, String from, String title, String body) {
        if (!canNotify()) return;
        PendingIntent pi = PendingIntent.getActivity(this, from.hashCode(),
                launchIntent(type, "", from, ""), flags());
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, channel)
                .setSmallIcon(android.R.drawable.sym_action_chat)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi);
        NotificationManagerCompat.from(this).notify(id, b.build());
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationChannel calls = new NotificationChannel(CH_CALLS, "आने वाली कॉल", NotificationManager.IMPORTANCE_HIGH);
        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        calls.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), aa);
        calls.enableVibration(true);
        calls.setVibrationPattern(new long[]{0, 800, 400, 800, 400, 800});
        calls.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(calls);

        nm.createNotificationChannel(new NotificationChannel(CH_MESSAGES, "संदेश", NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel(CH_MISSED, "मिस्ड कॉल", NotificationManager.IMPORTANCE_DEFAULT));
    }
}
