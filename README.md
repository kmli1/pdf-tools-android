# PDF Atölyesi + 0.2.0

Mevcut temayı koruyan yeni sürüm. Araçlar sekmesinde 26 seçenek vardır: fotoğraf/kamera/metin/boş PDF, birleştirme, çıkarma, bölme, sıralama, döndürme, silme, çoğaltma, ters sıralama, çizim/metin, filigran, numara, üst bilgi, JPG/PNG, TXT, OCR, metin arama, sıkıştırma, parola ekleme/kaldırma, form, başlık/yazar ve belge bilgileri.

Android 8.0+ için debug APK. `com.pdfatolyesi.app.v2` kimliğiyle eski uygulamanın yanına kurulur. Eski belgeler otomatik aktarılmaz; önceki uygulamadan farklı kaydedilip yeni uygulamada açılabilir.

Fotoğrafta renk/gri/siyah-beyaz, döndürme, simetrik kenar kırpma, sıralama, kalite, A4/Letter/otomatik boyut ve kenar boşluğu ayarı vardır. Kamera sistem kamera uygulamasını kullanır. OCR gömülü Latin modeliyle cihazda TXT üretir; aranabilir PDF oluşturmaz. Türkçe doğruluğu cihazda kontrol edilmelidir.

Çizim ekranında kalem/imza, yeni metin, vurgu, dikdörtgen ve çizgi; renk ve boyut, geri al/yinele, kalıcı taslak ve PDF’ye aktarma bulunur. Mevcut PDF metni değiştirilmez. Çizilen imza elektronik imza değildir; şekille kapatma gerçek redaksiyon değildir.

Şifreli PDF görüntüleme Android 15+ gerektirir. Parola kaldırma bilinen sahip parolasıyla çalışır. Form desteği AcroForm metin alanları ve onay kutularıyla sınırlıdır. Sıkıştırma her dosyayı küçültmez. Otomatik/perspektif tarama, Office dönüşümleri ve sertifikalı imza yoktur.

## Android Studio

JDK 17, SDK 36, Build Tools 35.0.0. ZIP’i çıkarıp proje klasörünü açın; Gradle eşitleyin ve app’i çalıştırın. İlk derleme internet ister.

`./gradlew :app:assembleDebug`

## Doğrulama

Bu teslimatta APK derlemesi ve imzası kontrol edilir. Kullanıcının isteğiyle ek test/lint turları tekrarlanmaz. Önceki sürümün testleri bu yeni sürümün bütün özelliklerini doğrulamış sayılmaz. Kamera, OCR ve dokunmatik çizim için fiziksel cihaz/emülatör testi yapılmadı.

PDFBox-Android 2.0.27.0 (Apache 2.0), ExifInterface 1.4.2, ML Kit Text Recognition 16.0.1 (Google koşulları), DejaVu Sans kullanılır. Font bildirimi licenses/DejaVu-LICENSE.txt dosyasındadır. PDFBox kaynağı: https://github.com/TomRoush/PdfBox-Android ; OCR belgesi: https://developers.google.com/ml-kit/vision/text-recognition/v2/android
