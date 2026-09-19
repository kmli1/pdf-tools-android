# Üçüncü taraf bileşenleri

Bu dosya bağımlılık seçimini ve kaynaklarını açıklar. Dağıtım öncesi çözümlenmiş tüm geçişli bağımlılıkların LICENSE/NOTICE dosyaları korunmalıdır.

| Bileşen | Lisans / kaynak |
|---|---|
| Kotlin ve kotlinx.coroutines | Apache 2.0 — https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt ve https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt |
| AndroidX Compose, Material 3, Activity, Lifecycle, Navigation, Room, DataStore, Core | Apache 2.0 — https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt |
| Android Gradle Plugin | Apache 2.0 — Android build araçları; uygulamanın runtime PDF motoru değildir |
| Gradle Wrapper | Apache 2.0 — https://github.com/gradle/gradle/blob/v8.13.0/LICENSE |
| JUnit 4 (test) | EPL 1.0 — https://github.com/junit-team/junit4/blob/main/LICENSE-junit.txt |
| Hamcrest (JUnit geçişli test bağımlılığı) | BSD 3-Clause — test kapsamı, APK’ya bilerek eklenmez |
| Android PdfRenderer / PdfDocument | Android framework API; cihaz tarafından sağlanır. Uygulamada ayrıca PDFium binary dağıtımı yoktur. |

Apache 2.0 bileşenler ticari dağıtımı açık kaynak yayınlama zorunluluğu getirmeden destekler; lisans/NOTICE bildirimleri ve diğer şartlar korunmalıdır. Bu projeye ücretli PDF SDK, AGPL motor veya ML Kit eklenmedi. ML Kit, PDFBox-Android ve diğer motorlar teknik planda aday olarak geçer; gelecekte eklenmeleri ayrıca bağımlılık ve koşul incelemesi gerektirir.

Projenin kendi yeni kaynak kodları kullanıcıya teslim edilir; burada kullanıcı adına bir genel yayın lisansı seçilmedi.

## 0.2.0 güncellemesi

Önceki sürümde aday olarak belirtilen PDFBox-Android 2.0.27.0 (Apache 2.0) ve ML Kit Text Recognition 16.0.1 (Google SDK koşulları) bu sürüme eklenmiştir. AndroidX ExifInterface 1.4.2 de kullanılır. DejaVu Sans lisans bildirimi `licenses/DejaVu-LICENSE.txt` altındadır. Önceki metindeki "eklenmedi" ifadesi yalnızca 0.1.0 sürümünü anlatır.
