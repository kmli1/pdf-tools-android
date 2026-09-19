# Teknik kararlar ve uygulanabilirlik

Karar tarihi: 18 Eylül 2026. “En yeni sürüm” varsayımı yerine resmi kaynaklarda ve Maven yayınlarında doğrulanan, sabitlenmiş kararlı sürümler kullanılmıştır. Bu belge proje planını da içerir; gelecek aşama adayları uygulamaya eklenmiş bağımlılıklar değildir.

## Yetenek matrisi

| Özellik | API/kütüphane | Lisans/dağıtım etkisi | Çevrimdışı | Kısıt ve karar |
|---|---|---|---|---|
| PDF görüntüleme | Android PdfRenderer | Sistem API; AOSP/PDFium bileşenlerini cihaz sağlar; uygulamaya ayrı PDF motoru paketi eklenmez | Evet | Aşama 1: sayfa render; metin içerik düzenleme sunmaz |
| Arayüz, navigasyon | AndroidX Compose/Material 3/Navigation | Apache 2.0 | Evet | Türkçe kaynak dosyaları; İngilizce çeviri dosyası sonradan eklenebilir |
| Katalog/geçmiş | Room | Apache 2.0 | Evet | Uygulamaya özel DB; otomatik bulut yedeği kapalı |
| Ayarlar | Preferences DataStore | Apache 2.0 | Evet | Tema ayarı |
| Yeni fotoğraf PDF’si | Android PdfDocument, Photo Picker, AndroidX ExifInterface | Sistem API ve Apache 2.0 AndroidX | Evet, seçilen görsel yerelse | Aşama 2; mevcut PDF’yi açıp düzenleme yeteneği değildir |
| Kamera | CameraX; dört nokta dönüşümü için Android Matrix/Canvas | Apache 2.0 / sistem API | Evet | Aşama 3; elle köşe seçimi mutlaka gerçek perspektif dönüşümüyle tamamlanacak |
| OCR | Aday: ML Kit Text Recognition v2, uygulamaya gömülü Latin modeli | Google SDK/API koşulları; Apache 2.0 diye etiketlenemez | Model paketlendiğinde cihaz içi | Aşama 6; Türkçe destek listesinde, doğruluk garantisi değildir. Model/telemetri koşulları entegrasyonda gözden geçirilecek |
| Sayfa birleştirme/bölme, üzerine çizim/metin | Aday: PDFBox-Android | Apache 2.0; ek şifreleme/font bileşenlerinin lisansları ayrıca incelenmeli | Evet | Android portunun upstream sürüm/güvenlik farkı araştırılmadan ana motora alınmadı; bu sürümde ekli değil |
| Mevcut metni yerleşimi koruyarak değiştirme | Ayrı gelişmiş editör adaptörü | Ticari motor gerekebilir; lisans seçimi ayrı ürün kararı | Motora bağlı | Temel sistem render API’siyle güvenilir biçimde desteklenmiyor |
| Açılış parolasıyla görüntüleme | PdfRenderer + LoadParams (API 35+) | Sistem API | Evet | Doğru parola ve desteklenen güvenlik şeması gerekli; eski Android’de açıklama |
| Parola ekleme/kaldırma, form doldurma | Sonraki PDF mutation motoru | Motor seçimine bağlı | Motora bağlı | Aşama 6; yetki kontrolü, standart AcroForm ve XFA ayrımı gerekli |
| PDF → PNG/JPG | PdfRenderer + Bitmap sıkıştırma | Sistem API | Evet | Aşama 4; sayfa sayfa dosyalama, çözünürlük sınırı, ZIP paketleme |
| Gerçek sıkıştırma | PDF nesnelerine erişen motor, görselleri yeniden örnekleme | Motora bağlı | Evet | Aşama 6; salt yeniden kaydetme sıkıştırma sayılmaz; rasterleştirme açık kalite uyarısıyla alternatif olabilir |
| İş kuyruğu | AndroidX WorkManager | Apache 2.0 | Evet | Uzun ve ertelenebilir dönüşümler başlayınca eklenecek; anlık okuma için gereksiz bağımlılık eklenmedi |

## Sabitlenen araç zinciri

| Bileşen | Sürüm / tercih | Gerekçe |
|---|---|---|
| JDK | 17 | AGP 8.13 gereksinimi |
| Gradle Wrapper | 8.13 | Resmi uyumluluk tablosu; dağıtım SHA-256 sabitlendi |
| Android Gradle Plugin | 8.13.2 | API 36 desteği; kararlı geçmiş sürüm |
| Kotlin / Compose compiler plugin / kapt | 2.2.21 | Compiler plugin Kotlin ile aynı sürüm; kapt Room annotation processor için |
| Compose BOM | 2025.10.01 | UI/Material3 bağımlılıkları tek BOM üzerinden |
| Activity | 1.11.0 | API 36 ile derlenen kararlı sürüm |
| Lifecycle | 2.9.4 | StateFlow lifecycle toplama ve ViewModel |
| Navigation Compose | 2.9.5 | Gerçek ekran yığını |
| Room | 2.8.3 | Room veritabanı ve şema çıktısı |
| DataStore | 1.1.7 | Kalıcı tema tercihi |
| Core KTX | 1.17.0 | FileProvider ve AndroidX desteği |
| Coroutines Android | 1.10.2 | IO işlemleri ve iptal |
| minSdk | 26 | Android 8+ ilk destek tabanı; PdfRenderer’ın API 21 tabanından daha yüksek, bilinçli ürün/test kapsamı tercihi |
| compileSdk / targetSdk | 36 / 36 | 31 Ağustos 2026 Play yeni uygulama/güncelleme gereksinimini karşılar |

Kapt, yeni projelerde tercih edilen KSP’ye alternatif olarak ilk sürümün işlemci uyumluluğunu sade tutmak için kullanıldı. KSP geçişi Room şeması ve testleri korunarak yapılabilir. Bağımlılıklar `gradle/libs.versions.toml` içinde; sürüm aralığı veya dinamik `+` yoktur.

## Mimari ve dosya akışı

Compose ekranı → ViewModel / StateFlow → DocumentRepository → Room + uygulamaya özel dosyalar. PDF tarafında UI yalnız `PdfSession` arayüzünü kullanır; `AndroidPdfEngine` sistem API adaptörüdür. Bağımlılıklar Application üzerinden elle sağlanır; Hilt gerektirmeyen küçük bir composition root kullanılır.

Dosya seçimi ACTION_OPEN_DOCUMENT tabanlı SAF ile yapılır. Content URI bir dosya yolu sayılmaz. Dosya `ContentResolver` akışıyla UUID adlı `.part` dosyasına sınırlı kopyalanır, PDF başlığı ve motorla açılabilmesi denetlenir, ardından yerel dosya adı atomik rename ile değiştirilir ve Room işlemi tamamlanır. Hata/iptalde kısmi dosya temizlenir. Süreç ani kapanırsa bir sonraki açılışta DB’de olmayan/yarım dosyalar temizlenir. Uygulama bir anlık kopya tuttuğu için kalıcı URI izni biriktirmez; kaynak dosyaya yeniden erişim gerekmiyor. Bu tercih dosya sağlayıcısı izin kaybını okuma akışından kaldırır; kaynakla canlı senkronizasyon sağlamaz.

PDF render IO dispatcher üzerinde mutex ile seri yapılır. Sayfa işlem sonunda kapanır; oturum ViewModel temizlenirken kapanır. 24 MiB bitmap LRU önbelleği, sayfa başına 4 megapiksel ve 2048 piksel hedef genişlik üst sınırı vardır. Görüntülenmekte olan bitmap’ler önbellekten atıldığında zorla recycle edilmez. Bu sınırlar toplam süreç RAM’inin 24 MiB olduğu anlamına gelmez; native motor ve görünür sayfalar ek bellek kullanır. Native render çağrısı yürürken iptal anında kesilemez; döndüğünde iptal kontrol edilir.

Üretim yayını öncesi, güvenilmeyen PDF’ler için PdfRenderer dokümanının önerdiği izole süreç servis mimarisi ayrıca değerlendirilmeli. İlk sürüm uygulama sürecinde render eder; kötü biçimlenmiş native dosyaya karşı tam izolasyon iddiası yoktur.

## Yanlış eşdeğerliklerden kaçınma

- Yeni metin katmanı ekleme, eski metnin font/yerleşimini koruyarak düzenlenmesi değildir.
- OCR metni TXT’ye yazma, PDF’ye hizalı görünmez metin katmanı eklemek değildir.
- Beyaz şekil çizmek güvenli redaksiyon değildir; bu üründe redaksiyon vaadi yoktur.
- Çizilen imza sertifikalı dijital imza değildir. İmzalı PDF içerik değişiklikleri doğrulamayı etkileyebilir.
- Rasterleştirme metin seçimini/aramayı kaybettirir. Sonraki sıkıştırma akışında kullanıcı işlem öncesi bilgilendirilmeli.
- Parola girişiyle açma, parola ekleme/kaldırma değildir; bu sürüm sadece görüntüler.

## Gelecek motor adaptörleri

Mevcut `PdfEngine/PdfSession` sadece render içindir. İleride `PdfCreationService`, `PdfPageService`, `PdfAnnotationService`, `OcrService`, `AdvancedContentEditor` birbirinden ayrı arayüzler olarak eklenecek. Yetenek bildirimi unsupported işlemlerin UI’da etkinleşmesini engelleyecek. Bu teslimata sahte başarılı dönen mutation metotları eklenmedi.

Düzenleme taslağı PDF noktaları cinsinden crop/media box ve rotation bilgisiyle tutulmalı. Viewport ölçek/öteleme matrisi ters çevrilerek PDF koordinatına geçilmeli. 0/90/180/270 derece sayfalar, farklı yoğunluklar ve crop box başlangıçları için round-trip testleri Aşama 5’in kabul kapısıdır. Bu ilk sürümde açıklama koordinat doğruluğu tamamlanmış sayılmaz.

## Resmi kaynaklar

18 Eylül 2026 tarihinde erişilen kaynaklar:

1. AGP/Gradle/JDK/API uyumu: https://developer.android.com/build/releases/agp-8-13-0-release-notes
2. Play target API gereksinimi: https://developer.android.com/google/play/requirements/target-sdk
3. Kotlin sürümleri: https://kotlinlang.org/docs/releases.html
4. Compose compiler yöntemi: https://developer.android.com/jetpack/androidx/releases/compose-kotlin
5. BOM: https://developer.android.com/develop/ui/compose/bom/bom-mapping
6. BOM yayın kaydı: https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2025.10.01/compose-bom-2025.10.01.pom
7. Activity: https://developer.android.com/jetpack/androidx/releases/activity
8. Lifecycle: https://developer.android.com/jetpack/androidx/releases/lifecycle
9. Navigation: https://developer.android.com/jetpack/androidx/releases/navigation
10. Room: https://developer.android.com/jetpack/androidx/releases/room
11. DataStore: https://developer.android.com/jetpack/androidx/releases/datastore
12. PdfRenderer: https://developer.android.com/reference/android/graphics/pdf/PdfRenderer
13. LoadParams: https://developer.android.com/reference/android/graphics/pdf/LoadParams.Builder
14. PdfDocument: https://developer.android.com/reference/android/graphics/pdf/PdfDocument
15. CameraX: https://developer.android.com/jetpack/androidx/releases/camera
16. ML Kit Android: https://developers.google.com/ml-kit/vision/text-recognition/v2/android
17. Türkçe dil desteği: https://developers.google.com/ml-kit/vision/text-recognition/v2/languages
18. ML Kit koşulları: https://developers.google.com/ml-kit/terms
19. PDFBox Android portu: https://github.com/TomRoush/PdfBox-Android
20. Gradle doğrulama: https://gradle.org/release-checksums/

PDFBox Android portunun belgesindeki 2.0.27.0 sürümü, upstream Apache PDFBox’ın aynı bakım seviyesinde olduğu anlamına gelmez. Bu adayın ticari üretim için güvenlik incelemesi yapılmadı. iText/MuPDF gibi copyleft veya ticari lisans seçenekli motorlar sessizce eklenmedi.
