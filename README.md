# Blackjack Köməkçisi (Android)

Oyunun ekranını oxuyur, kartları tanıyır və üzən pəncərədə Hit / Stand / Double / Split məsləhəti göstərir. Hi-Lo sayını da aparır.

## Quraşdırma

1. Bu repo-nun **Releases** səhifəsindən ən son `app-debug.apk` faylını yüklə.
2. Telefon "naməlum mənbə" icazəsi istəsə, brauzer üçün ver.
3. Tətbiqi aç, üzən pəncərə icazəsini ver, **Başla** düyməsinə bas.
4. Ekran paylaşma sorğusunu təsdiqlə və oyunu aç.

## Necə işləyir

- `CaptureService` ekranı MediaProjection ilə tutur.
- `CardReader` kartların sol üst küncündəki rəqəmi cihaz üzərində mətn tanıma ilə oxuyur.
- `RoundTracker` oxunuşları sabitləşdirir və hər əldən sonra sayı yeniləyir.
- `Strategy` basic strategy cədvəli və bir neçə Hi-Lo indeksidir.
- `Overlay` sürüşdürülə bilən kiçik pəncərədir.

Səhv oxuyursa üzən pəncərədəki **Debug** düyməsi `Pictures/BlackjackHelper` qovluğuna üstünə tanınan mətn qutuları çəkilmiş görüntü saxlayır.

## Build

GitHub Actions hər `main` push-unda debug APK qurur və Release kimi yükləyir (`.github/workflows/build.yml`).
