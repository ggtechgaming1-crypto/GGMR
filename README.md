# GG Messenger – असली Android ऐप (Capacitor)

इस प्रोजेक्ट से APK **GitHub Actions** (क्लाउड) में बनती है — आपको Android Studio नहीं चाहिए।

## इसमें क्या है
- कॉल आने पर notification में **✅ उठाएँ / ❌ काटें** बटन (ऐप बंद हो तब भी, लॉक स्क्रीन पर पूरी स्क्रीन)
- ❌ काटें दबाने पर ऐप खुले बिना कॉल कट जाती है
- notification पर टैप → ऐप खुलकर **उसी व्यक्ति की चैट** (या आती हुई कॉल) सामने
- कॉल में आवाज़ चुनने की शीट: **लाउडस्पीकर / फ़ोन (कान पर) / ब्लूटूथ / तार वाला हेडफ़ोन**
- हेडफ़ोन लगाने-निकालने पर आवाज़ अपने-आप बदलती है

## चरण 1 – Firebase (एक बार)
1. https://console.firebase.google.com → **Add project** (नाम: gg-messenger)
2. प्रोजेक्ट में **Android** आइकन → Package name: `com.gg.messenger` → Register
3. **google-services.json** डाउनलोड करें (इसे संभालकर रखें)
4. ⚙️ Project settings → **Service accounts** → **Generate new private key** → JSON फ़ाइल डाउनलोड

## चरण 2 – GitHub पर APK बनवाना
1. नया **private** repository बनाएँ और इस ज़िप की *सारी* फ़ाइलें/फ़ोल्डर अपलोड करें (`.github` फ़ोल्डर समेत)
2. Repository → Settings → Secrets and variables → Actions → **New repository secret**
   - नाम: `GOOGLE_SERVICES_JSON`
   - वैल्यू: google-services.json का **पूरा** टेक्स्ट
3. **Actions** टैब → *Build Android APK* → **Run workflow** (लगभग 10 मिनट)
4. पूरा होने पर नीचे **Artifacts → GG-Messenger-debug-apk** डाउनलोड करें, unzip करें, `app-debug.apk` इंस्टॉल करें

## चरण 3 – Render सर्वर
1. नई `server.js` डालें (ज़िप के साथ दी गई)
2. `package.json` की dependencies में `"firebase-admin"` जोड़ें
3. Render → Environment: `FIREBASE_SERVICE_ACCOUNT` = चरण 1 वाली private key JSON का **पूरा** टेक्स्ट
4. Deploy करें, और `https://<आपका-ऐप>.onrender.com/health` को UptimeRobot से हर 5 मिनट ping करवाएँ

## पहली बार चलाने पर
- माइक, कैमरा, नोटिफिकेशन, ब्लूटूथ की अनुमति दें
- Android 14+: "फ़ुल स्क्रीन नोटिफिकेशन" की अनुमति वाली सेटिंग्स खुलें तो चालू करें
- Xiaomi / Oppo / Vivo / Samsung: ऐप की बैटरी **Unrestricted** और Autostart **चालू** रखें
- टेस्ट: दो फ़ोन पर लॉगिन करें, एक की ऐप बंद करके दूसरे से कॉल करें

## ध्यान रखें
- हर बिल्ड की debug key अलग होती है: नई APK डालने से पहले पुरानी ऐप **uninstall** करें
- ऐप का HTML `www/index.html` में है; बदलाव करके push करें → नई APK अपने-आप बनेगी
- सर्वर का पता बदलना हो तो `.github/workflows/build-apk.yml` में `GG_SERVER_URL` बदलें
- बिल्ड फेल हो तो Actions में लाल (❌) स्टेप का लॉग भेजें
