# Firebase Chat

Anlık mesajlaşma odası (açık oda). Herkes mesajları okuyabilir.
Sadece uygulamayı (APK) kuran kişiler mesaj gönderebilir, fotoğraf paylaşabilir.

## Ne ücretsiz, ne ücretli?

- **Realtime Database** (Spark/free): mesajlar burada tutulur. Fotoğraflar sıkıştırılıp burada saklanır; ayrı Storage gerekmez.
- **Authentication (Anonymous)**: ücretsiz.
- **FCM bildirimleri**: ücretsiz altyapı var; bildirim göndermek için Cloud Function (Blaze) isteğe bağlıdır.
- **Cloud Functions / Blaze**: push bildirimini uygulama kapalıyken göndermek için gerekir. Açık değilse de uygulama içi anlık güncellemeler ve ön planda bildirimler çalışır.

## Firebase Console kurulumu (adım adım)

1. Firebase Console’da projeyi aç.
2. **Authentication → Sign-in method** -> **Anonymous** aç.
3. **Realtime Database** oluştur -> kuralları `database.rules.json` içeriğiyle değiştir.
   - Firebase CLI ile:
     ```powershell
     firebase login
     cd C:\Code\FirebaseChat
     firebase deploy --only database
     ```
4. **Project settings -> General -> Android app ekle**
   - package name: `com.example.firebasechat`
5. `google-services.json` indir ve `app/` klasörüne koy.

## Build

Android Studio ile projeyi aç -> Gradle Sync -> Build APK(s).

## APK nerede?

`app\build\outputs\apk\debug\app-debug.apk`

## Bildirimler (isteğe bağlı, Blaze gerekir)

```powershell
cd functions
npm install
cd ..
firebase deploy --only functions
```

## Kurallar dosyası
- `database.rules.json`
- (Firebase Storage kullanılmıyor; fotoğraflar RTDB içinde tutuluyor)
- `functions/` (isteğe bağlı)
