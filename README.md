# Blackjack Köməkçisi (Android)

Oyunun ekranını oxuyur, kartları tanıyır və üzən pəncərədə Hit / Stand / Double / Split məsləhəti göstərir. Hi-Lo sayını da aparır.

## Quraşdırma

1. Birbaşa yüklə: https://github.com/Islamli-Elxan/blackjack-helper/releases/latest/download/blackjack-helper.apk (həmişə ən son versiya, tək fayl).
2. Telefon "naməlum mənbə" icazəsi istəsə, brauzer üçün ver.
3. Tətbiqi aç, üzən pəncərə icazəsini ver, **Başla** düyməsinə bas.
4. Ekran paylaşma sorğusunu təsdiqlə və oyunu aç.

## Necə işləyir

- `CaptureService` ekranı MediaProjection ilə tutur.
- `CardReader` kartların sol üst küncündəki rəqəmi cihaz üzərində öz kiçik neyron şəbəkəsi ilə tanıyır (internet və Google xidməti lazım deyil).
- `RoundTracker` oxunuşları sabitləşdirir və hər əldən sonra sayı yeniləyir.
- `Strategy` basic strategy cədvəli və bir neçə Hi-Lo indeksidir.
- `Overlay` sürüşdürülə bilən kiçik pəncərədir.

Səhv oxuyursa üzən pəncərədəki **Debug** düyməsi `Pictures/BlackjackHelper` qovluğuna üstünə tanınan mətn qutuları çəkilmiş görüntü saxlayır.

## Build

GitHub Actions hər `main` push-unda APK qurur və `latest` Release-ini təzələyir (həmişə tək `blackjack-helper.apk`) (`.github/workflows/build.yml`).
