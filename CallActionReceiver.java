package __PACKAGE__;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationManagerCompat;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** notification के "❌ काटें" बटन पर: ऐप खोले बिना सर्वर को बताता है कि कॉल reject हुई। */
public class CallActionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationManagerCompat.from(context).cancel(GGMessagingService.NOTIF_CALL);

        final String to = intent.getStringExtra("to");
        final String from = intent.getStringExtra("from");
        final String callId = intent.getStringExtra("callId");
        final PendingResult pr = goAsync();

        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                URL url = new URL(GGConfig.SERVER_URL + "/reject-call");
                c = (HttpURLConnection) url.openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                JSONObject j = new JSONObject();
                j.put("to", to);
                j.put("from", from);
                j.put("callId", callId);
                OutputStream os = c.getOutputStream();
                os.write(j.toString().getBytes("UTF-8"));
                os.close();
                c.getResponseCode();
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.disconnect();
                pr.finish();
            }
        }).start();
    }
}
