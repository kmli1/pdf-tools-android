# Manuel kabul planı

Bu belge test adımlarıdır; testlerin çalıştırıldığını göstermez. Sonuçlar `TEST_SONUCLARI.md` içindedir.

## Aşama 1 cihaz matrisi

En az API 26, API 35/36; bir fiziksel telefon ve bir tablet; açık/koyu tema; %100 ve %200 yazı boyutu. Her testte cihaz modeli, OS, APK SHA-256, dosya boyutu ve sayfa sayısı kaydedilir.

1. Temiz kurulum: uygulama açılır, Türkçe boş durum ve dört navigasyon öğesi görünür. Ağ/depolama izni istenmez.
2. Yerel üç sayfalık PDF seçin. Sayfa 1/2/3 içeriği doğru sırada görünmeli; kaynak dosyanın SHA-256 özeti aynı kalmalı.
3. 1’den 3’e kaydırın; küçük resimden sayfa 2’ye geçin; git kutusunda 0, -1, 4, harf ve boş girdi reddedilmeli. 3 kabul edilmeli.
4. İki parmakla büyütün, sürükleyin, sığdırın. 1x’te tek parmak dikey kaydırma çalışmalı. Zoom düğmelerinin TalkBack etiketleri anlaşılır olmalı.
5. İkinci sayfada cihazı döndürün; arayüz kullanılabilir olmalı. Uygulamayı kapatıp yeniden açın, aynı belgenin son sayfasına dönmeli. Süreç öldürme sonrası da deneyin.
6. Favori ekleyin/çıkarın; `İ`, `ı`, `ş`, `ğ` içeren isimle arayın; tarihe/isme/boyuta göre sıralamayı kontrol edin. Yeniden adlandırma kaynak adını değiştirmemeli.
7. Kaldırmada iptal ve onay yolunu deneyin. Onay sonrası katalog ve özel kopya kalkmalı, kaynak dosya kalmalı.
8. Paylaşımı bağımsız bir PDF okuyucuya yapın. İsim ve içerik görünmeli; FileProvider URI kullanıldığını doğrulayın. Farklı kaydetme çıktısı kaynakla byte-byte aynı olmalı.
9. Boş dosya, HTML içeren `.pdf`, eksik xref’li bozuk PDF ve desteklenmeyen güvenlik türünü deneyin. Yönetilebilir hata veya sayfa tekrar deneme görünmeli; native çökme varsa ayrıca kaydedin.
10. API 35+ üzerinde parolalı PDF: yanlış parola tekrar istemeli; doğru parola açmalı; süreç öldürülünce tekrar parola istemeli. API 26’da açık destek sınırı gösterilmeli.
11. 150 MiB üzeri dosya reddedilmeli. Kopyalama sırasında iptal edin; katalogda yarım kayıt ve `.part` dosyası kalmamalı. İşlem sırasında süreç öldürülürse sonraki açılışta artıklar temizlenmeli.
12. Hedef SAF sağlayıcısında yazma izni kesilmesi/alan dolması deneyin. Başarı mesajı çıkmamalı. Sağlayıcı silmeyi destekliyorsa yarım hedef kalkmalı; aksi durumda kalan hedef kaydedilmeli.
13. Yerel belge ile uçak modunda açma, yeniden açma ve gezinme çalışmalı. Bulut sağlayıcısındaki indirilmeyen dosyanın çevrimdışı erişimi sağlayıcıya bağlıdır.
14. 100 sayfalık, 10/50/100 MiB örneklerle ilk sayfa süresi, p95 render zamanı ve peak RSS ölçün. Sabit performans hedefi test cihazı seçilmeden ilan edilmez.
15. Kaynak dosya silindikten sonra uygulamadaki özel kopya açılmalı. Uygulama verileri temizlenince özel kopyanın da silindiği kullanıcı akışıyla doğrulanmalı.

## Sonraki aşamaların kabul kapıları

- 10 fotoğraf sırası, EXIF ve Türkçe PDF çıktısı: Aşama 2/5, mevcut sürüme uygulanamaz.
- Kamera elle köşe seçimi, perspektif ve yeniden çekim: Aşama 3, uygulanamaz.
- Birleştirme/bölme sayfa sırası/sayısı: Aşama 4, uygulanamaz.
- Metin/imza yeniden açıldığında aynı konum; döndürülmüş/zoom yapılmış sayfalar: Aşama 5, uygulanamaz.
- OCR Türkçe karakterler, aranabilir PDF metin katmanı, sıkıştırma ve form değişiklikleri: Aşama 6, uygulanamaz.

Bu senaryoları ilk sürüm için “geçti” saymayın.
