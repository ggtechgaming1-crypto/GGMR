package __PACKAGE__;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    // ऐप स्क्रीन पर है या नहीं (स्क्रीन पर हो तो notification की जगह ऐप खुद कॉल/मैसेज दिखाता है)
    static volatile boolean visible = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(GGNativePlugin.class);
        super.onCreate(savedInstanceState);
        applyLockScreenMode(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyLockScreenMode(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        visible = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        visible = false;
    }

    // सिर्फ़ आने वाली कॉल पर लॉक स्क्रीन के ऊपर दिखाएँ और स्क्रीन जलाएँ
    private void applyLockScreenMode(Intent intent) {
        boolean isCall = intent != null && "incoming_call".equals(intent.getStringExtra("gg_type"));
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(isCall);
            setTurnScreenOn(isCall);
        }
    }
}
