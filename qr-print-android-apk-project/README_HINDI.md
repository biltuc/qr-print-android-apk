# QR Print — Android Customer APK प्रोजेक्ट

यह पूरा Android Studio + GitHub Actions प्रोजेक्ट है जिससे आप **असली .apk फाइल** बना सकते हैं।
इस APK को ग्राहक अपने फोन में इंस्टॉल करके सीधे किसी भी कैफे का QR स्कैन कर पाएँगे और **Document Print, ID Card, Passport Photo** सभी फीचर्स इस्तेमाल कर पाएँगे।

## तरीका 1: बिना Android Studio के 2 मिनट में APK बनाएं (सबसे आसान — GitHub Actions)

1. GitHub पर एक नया रिपॉजिटरी (Repository) बनाइए (जैसे: `qr-print-apk`)।
2. इस ZIP को Extract करके सभी फाइलें (खासकर `.github/workflows/build-apk.yml`) उस रिपॉजिटरी में अपलोड / push कर दीजिए।
3. GitHub में ऊपर **Actions** टैब खोलिए > **Build Customer Android APK** पर क्लिक कीजिए।
4. ~2 मिनट बाद बिल्ड हरा (✅) हो जाएगा। नीचे **Artifacts** में **QR-Print-Customer-APK** पर क्लिक कीजिए।
5. डाउनलोड हुई ZIP खोलिए — अंदर **`app-debug.apk`** तैयार मिलेगी! इसे WhatsApp या फोन में भेजकर सीधे Install कीजिए।

## तरीका 2: अपने PC पर Android Studio से APK बनाएं

1. Android Studio खोलिए > **Open** > इस Extract किए हुए फोल्डर को चुनिए।
2. Gradle Sync पूरा होने दीजिए।
3. ऊपर मेन्यू में **Build > Build Bundle(s) / APK(s) > Build APK(s)** दबाइए।
4. APK यहाँ तैयार मिलेगी: `app/build/outputs/apk/debug/app-debug.apk`

## इसमें क्या-क्या पहले से शामिल है
- **Native CameraX + Google ML Kit QR Scanner**: ऐप खोलते ही तेज़ QR स्कैनर (फ्लैशलाइट बटन के साथ)।
- **सभी कैफे लिंक सपोर्ट**: `https://qr-print-ochre.vercel.app/c/<slug>` स्कैन होते ही सीधे उस कैफे का पेज खुलता है।
- **फाइल और कैमरा अपलोड (FileChooser)**: Document PDF, ID Card (Front/Back) और Passport Photo अपलोड करने के लिए गैलरी और कैमरा दोनों काम करते हैं।
- **बॉटम बार**: ग्राहक कभी भी **QR स्कैन करें**, **मेन्यू**, या **रीलोड** दबा सकता है।
