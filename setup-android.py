#!/usr/bin/env python3
"""
`npx cap add android` के बाद चलाएँ। यह स्क्रिप्ट:
  1. GG की नेटिव Java फ़ाइलें android प्रोजेक्ट में डालती है
  2. AndroidManifest.xml में अनुमतियाँ, FCM service और reject receiver जोड़ती है
  3. Firebase Messaging dependency जोड़ती है और minSdk 24 करती है
बार-बार चलाने पर भी सुरक्षित है।
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
cfg = json.load(open(os.path.join(ROOT, 'capacitor.config.json'), encoding='utf-8'))
APP_ID = cfg['appId']
SERVER_URL = os.environ.get('GG_SERVER_URL', 'https://gg-message.onrender.com').rstrip('/')

ANDROID = os.path.join(ROOT, 'android')
APP = os.path.join(ANDROID, 'app')
MAIN = os.path.join(APP, 'src', 'main')
JAVA_DIR = os.path.join(MAIN, 'java', *APP_ID.split('.'))
MANIFEST = os.path.join(MAIN, 'AndroidManifest.xml')

for p in (ANDROID, MANIFEST):
    if not os.path.exists(p):
        sys.exit('नहीं मिला: %s  (पहले `npx cap add android` चलाएँ)' % p)

# 1. Java फ़ाइलें
os.makedirs(JAVA_DIR, exist_ok=True)
src_dir = os.path.join(ROOT, 'native', 'java')
for name in sorted(os.listdir(src_dir)):
    if not name.endswith('.java'):
        continue
    text = open(os.path.join(src_dir, name), encoding='utf-8').read()
    text = text.replace('__PACKAGE__', APP_ID).replace('__SERVER_URL__', SERVER_URL)
    open(os.path.join(JAVA_DIR, name), 'w', encoding='utf-8').write(text)
    print('java  :', name)

# 2. Manifest
m = open(MANIFEST, encoding='utf-8').read()
perms = [
    'android.permission.INTERNET',
    'android.permission.ACCESS_NETWORK_STATE',
    'android.permission.RECORD_AUDIO',
    'android.permission.CAMERA',
    'android.permission.MODIFY_AUDIO_SETTINGS',
    'android.permission.POST_NOTIFICATIONS',
    'android.permission.USE_FULL_SCREEN_INTENT',
    'android.permission.VIBRATE',
    'android.permission.WAKE_LOCK',
    'android.permission.BLUETOOTH_CONNECT',
]
add_top = ''
for p in perms:
    if p not in m:
        add_top += '    <uses-permission android:name="%s" />\n' % p
for feat in ('android.hardware.camera', 'android.hardware.microphone'):
    if feat not in m:
        add_top += '    <uses-feature android:name="%s" android:required="false" />\n' % feat
if add_top:
    i = m.index('<application')
    m = m[:i] + add_top.lstrip() + '\n    ' + m[i:]

if 'GGMessagingService' not in m:
    comps = (
        '\n        <service android:name="%(id)s.GGMessagingService" android:exported="false">\n'
        '            <intent-filter>\n'
        '                <action android:name="com.google.firebase.MESSAGING_EVENT" />\n'
        '            </intent-filter>\n'
        '        </service>\n'
        '        <receiver android:name="%(id)s.CallActionReceiver" android:exported="false" />\n'
    ) % {'id': APP_ID}
    i = m.rindex('</application>')
    m = m[:i] + comps + '    ' + m[i:]
open(MANIFEST, 'w', encoding='utf-8').write(m)
print('manifest: ok')

# 3. Gradle
gradle = os.path.join(APP, 'build.gradle')
g = open(gradle, encoding='utf-8').read()
if 'firebase-messaging' not in g:
    g += '\n\n// GG: Firebase push (FCM)\ndependencies {\n' \
         "    implementation platform('com.google.firebase:firebase-bom:32.7.4')\n" \
         "    implementation 'com.google.firebase:firebase-messaging'\n}\n"
    open(gradle, 'w', encoding='utf-8').write(g)
print('gradle: firebase-messaging')

var = os.path.join(ANDROID, 'variables.gradle')
if os.path.exists(var):
    v = open(var, encoding='utf-8').read()
    v2 = re.sub(r'minSdkVersion\s*=\s*\d+', 'minSdkVersion = 24', v)
    if v2 != v:
        open(var, 'w', encoding='utf-8').write(v2)
        print('variables.gradle: minSdkVersion = 24')

print('सब हो गया -> अब google-services.json को android/app/ में रखें और build करें')
