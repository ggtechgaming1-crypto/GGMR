package __PACKAGE__;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * JS से बुलाया जाने वाला नेटिव प्लगइन (Capacitor.registerPlugin('GGNative')):
 *  - FCM टोकन, अनुमतियाँ, फ़ुल-स्क्रीन कॉल की अनुमति
 *  - नोटिफिकेशन टैप/बटन से आया इरादा (किसकी चैट, कॉल उठाओ)
 *  - कॉल की आवाज़: लाउडस्पीकर / फ़ोन (कान पर) / ब्लूटूथ / तार वाला हेडफ़ोन
 */
@CapacitorPlugin(
        name = "GGNative",
        permissions = {
                @Permission(strings = {"android.permission.RECORD_AUDIO"}, alias = "microphone"),
                @Permission(strings = {"android.permission.CAMERA"}, alias = "camera"),
                @Permission(strings = {"android.permission.POST_NOTIFICATIONS"}, alias = "notifications"),
                @Permission(strings = {"android.permission.BLUETOOTH_CONNECT"}, alias = "bluetooth")
        }
)
public class GGNativePlugin extends Plugin {

    private JSObject launchData = null;

    private boolean callAudioActive = false;
    private String currentRoute = "earpiece";
    private AudioFocusRequest focusRequest = null;
    private AudioDeviceCallback deviceCallback = null;

    // ---------------- Launch / notification intent ----------------

    @Override
    public void load() {
        Intent i = getActivity().getIntent();
        launchData = intentToJs(i);
        if (launchData != null) clearExtras(i);
    }

    @Override
    protected void handleOnNewIntent(Intent intent) {
        super.handleOnNewIntent(intent);
        JSObject data = intentToJs(intent);
        if (data != null) {
            clearExtras(intent);
            notifyListeners("notificationAction", data, true);
        }
    }

    @PluginMethod
    public void getLaunchIntent(PluginCall call) {
        JSObject r = launchData != null ? launchData : new JSObject();
        launchData = null;
        call.resolve(r);
    }

    private JSObject intentToJs(Intent i) {
        if (i == null || !i.hasExtra("gg_type")) return null;
        JSObject o = new JSObject();
        o.put("type", i.getStringExtra("gg_type"));
        o.put("action", i.getStringExtra("gg_action"));
        o.put("from", i.getStringExtra("gg_from"));
        o.put("callId", i.getStringExtra("gg_callId"));
        return o;
    }

    private void clearExtras(Intent i) {
        i.removeExtra("gg_type");
        i.removeExtra("gg_action");
        i.removeExtra("gg_from");
        i.removeExtra("gg_callId");
    }

    // ---------------- FCM token ----------------

    @PluginMethod
    public void getFcmToken(PluginCall call) {
        try {
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                if (!task.isSuccessful() || task.getResult() == null) {
                    call.reject("FCM token नहीं मिला");
                    return;
                }
                JSObject r = new JSObject();
                r.put("token", task.getResult());
                call.resolve(r);
            });
        } catch (Exception e) {
            call.reject("Firebase चालू नहीं है (google-services.json जाँचें)");
        }
    }

    // ---------------- Permissions ----------------

    @PluginMethod
    public void askPermissions(PluginCall call) {
        List<String> aliases = new ArrayList<>();
        aliases.add("microphone");
        aliases.add("camera");
        if (Build.VERSION.SDK_INT >= 33) aliases.add("notifications");
        if (Build.VERSION.SDK_INT >= 31) aliases.add("bluetooth");
        requestPermissionForAliases(aliases.toArray(new String[0]), call, "permsDone");
    }

    @PermissionCallback
    private void permsDone(PluginCall call) {
        JSObject r = new JSObject();
        for (String a : new String[]{"microphone", "camera", "notifications", "bluetooth"}) {
            PermissionState s = getPermissionState(a);
            r.put(a, s == null ? "unknown" : s.toString().toLowerCase());
        }
        call.resolve(r);
    }

    @PluginMethod
    public void canUseFullScreenIntent(PluginCall call) {
        boolean allowed = true;
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
            allowed = nm.canUseFullScreenIntent();
        }
        JSObject r = new JSObject();
        r.put("allowed", allowed);
        call.resolve(r);
    }

    @PluginMethod
    public void openFullScreenIntentSettings(PluginCall call) {
        try {
            Intent i;
            if (Build.VERSION.SDK_INT >= 34) {
                i = new Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT",
                        Uri.parse("package:" + getContext().getPackageName()));
            } else {
                i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getContext().getPackageName());
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject("सेटिंग्स नहीं खुलीं");
        }
    }

    // ---------------- Call audio routing ----------------

    private AudioManager audio() {
        return (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
    }

    private boolean isType(AudioDeviceInfo d, String route) {
        int t = d.getType();
        switch (route) {
            case "speaker":
                return t == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
            case "earpiece":
                return t == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE;
            case "bluetooth":
                return t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                        || (Build.VERSION.SDK_INT >= 31 && t == AudioDeviceInfo.TYPE_BLE_HEADSET);
            case "wired":
                return t == AudioDeviceInfo.TYPE_WIRED_HEADSET
                        || t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                        || t == AudioDeviceInfo.TYPE_USB_HEADSET;
            default:
                return false;
        }
    }

    private List<AudioDeviceInfo> devices(AudioManager am) {
        if (Build.VERSION.SDK_INT >= 31) return am.getAvailableCommunicationDevices();
        return Arrays.asList(am.getDevices(AudioManager.GET_DEVICES_OUTPUTS));
    }

    private boolean has(AudioManager am, String route) {
        for (AudioDeviceInfo d : devices(am)) {
            if (isType(d, route)) return true;
        }
        return false;
    }

    private String bestDefaultRoute(AudioManager am) {
        if (has(am, "bluetooth")) return "bluetooth";
        if (has(am, "wired")) return "wired";
        return has(am, "earpiece") ? "earpiece" : "speaker";
    }

    private JSObject routesInfo() {
        AudioManager am = audio();
        JSArray arr = new JSArray();
        arr.put("speaker");
        if (has(am, "earpiece")) arr.put("earpiece");
        if (has(am, "wired")) arr.put("wired");
        if (has(am, "bluetooth")) arr.put("bluetooth");
        JSObject o = new JSObject();
        o.put("routes", arr);
        o.put("current", currentRoute);
        return o;
    }

    @SuppressWarnings("deprecation")
    private void applyRoute(String route) {
        AudioManager am = audio();
        if (Build.VERSION.SDK_INT >= 31) {
            AudioDeviceInfo target = null;
            for (AudioDeviceInfo d : am.getAvailableCommunicationDevices()) {
                if (isType(d, route)) {
                    target = d;
                    break;
                }
            }
            if (target != null) {
                am.setCommunicationDevice(target);
            } else if ("speaker".equals(route)) {
                am.setSpeakerphoneOn(true);
            }
        } else {
            if ("bluetooth".equals(route)) {
                am.setSpeakerphoneOn(false);
                am.startBluetoothSco();
                am.setBluetoothScoOn(true);
            } else {
                if (am.isBluetoothScoOn()) {
                    am.setBluetoothScoOn(false);
                    am.stopBluetoothSco();
                }
                am.setSpeakerphoneOn("speaker".equals(route));
            }
        }
        currentRoute = route;
    }

    private void registerDeviceCallback(AudioManager am) {
        if (deviceCallback != null) return;
        deviceCallback = new AudioDeviceCallback() {
            @Override
            public void onAudioDevicesAdded(AudioDeviceInfo[] added) {
                if (!callAudioActive) return;
                for (AudioDeviceInfo d : added) {
                    if (isType(d, "wired")) {
                        applyRoute("wired");
                        break;
                    }
                    if (isType(d, "bluetooth")) {
                        applyRoute("bluetooth");
                        break;
                    }
                }
                notifyListeners("audioRoutesChanged", routesInfo());
            }

            @Override
            public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
                if (!callAudioActive) return;
                AudioManager a = audio();
                boolean lost = ("wired".equals(currentRoute) && !has(a, "wired"))
                        || ("bluetooth".equals(currentRoute) && !has(a, "bluetooth"));
                if (lost) applyRoute(has(a, "earpiece") ? "earpiece" : "speaker");
                notifyListeners("audioRoutesChanged", routesInfo());
            }
        };
        am.registerAudioDeviceCallback(deviceCallback, new Handler(Looper.getMainLooper()));
    }

    @PluginMethod
    @SuppressWarnings("deprecation")
    public void startCallAudio(PluginCall call) {
        AudioManager am = audio();
        callAudioActive = true;
        am.setMode(AudioManager.MODE_IN_COMMUNICATION);
        if (Build.VERSION.SDK_INT >= 26) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .setOnAudioFocusChangeListener(change -> { })
                    .build();
            am.requestAudioFocus(focusRequest);
        } else {
            am.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
        registerDeviceCallback(am);
        boolean speaker = Boolean.TRUE.equals(call.getBoolean("speaker", false));
        applyRoute(speaker ? "speaker" : bestDefaultRoute(am));
        call.resolve(routesInfo());
    }

    @PluginMethod
    public void setAudioRoute(PluginCall call) {
        String route = call.getString("route", "speaker");
        applyRoute(route);
        call.resolve(routesInfo());
    }

    @PluginMethod
    public void getAudioRoutes(PluginCall call) {
        call.resolve(routesInfo());
    }

    @PluginMethod
    public void stopCallAudio(PluginCall call) {
        stopAudioInternal();
        call.resolve();
    }

    @SuppressWarnings("deprecation")
    private void stopAudioInternal() {
        if (!callAudioActive) return;
        callAudioActive = false;
        AudioManager am = audio();
        if (Build.VERSION.SDK_INT >= 31) {
            am.clearCommunicationDevice();
        } else {
            am.setSpeakerphoneOn(false);
            if (am.isBluetoothScoOn()) {
                am.setBluetoothScoOn(false);
                am.stopBluetoothSco();
            }
        }
        am.setMode(AudioManager.MODE_NORMAL);
        if (Build.VERSION.SDK_INT >= 26 && focusRequest != null) {
            am.abandonAudioFocusRequest(focusRequest);
            focusRequest = null;
        }
        if (deviceCallback != null) {
            am.unregisterAudioDeviceCallback(deviceCallback);
            deviceCallback = null;
        }
        currentRoute = "earpiece";
    }

    @Override
    protected void handleOnDestroy() {
        stopAudioInternal();
        super.handleOnDestroy();
    }
}
