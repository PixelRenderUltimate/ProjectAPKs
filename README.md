# PixelRender - PHASE 1 sampai 7

PHASE 1: deteksi perangkat, GPU, Vulkan, OpenGL ES, Shizuku + capability matrix.
PHASE 2: Shizuku user service, allowlist command, eksekusi read-only, logging, baseline.
PHASE 3: backend abstraction, matriks dirakit dari pendapat tiap backend.
PHASE 4: kontrol resolusi render lewat IWindowManager, dengan restore terverifikasi.
PHASE 5: profil Default / Pixel Low / Pixel Medium / Pixel Extreme / Custom.
PHASE 6: backup dan pemulihan yang bertahan melewati force-close dan reboot.
PHASE 7: unit test di CI, uji otomatis di perangkat, dan laporan diagnostik.

Satu-satunya yang ditulis aplikasi ini ke perangkat adalah ukuran dan density
display. Tidak ada yang lain.

## Upload ke GitHub

Yang di-upload adalah **isi** folder `PixelRender`, bukan foldernya. Setelah
upload, `settings.gradle.kts` dan folder `.github` harus langsung berada di
root repo. Kalau tidak, GitHub Actions tidak akan menemukan workflow-nya.

**Cara paling aman (git):**

```bash
cd PixelRender
git init
git add .
git commit -m "PixelRender"
git branch -M main
git remote add origin https://github.com/USERNAME/NAMA-REPO.git
git push -u origin main
```

**Lewat browser (Add file > Upload files):** seret semua isi folder. Setelah
itu periksa bahwa `.github/workflows/build-apk.yml`, `.gitignore`, dan
`.gitattributes` ikut ter-upload. Sebagian browser dan OS melewatkan
file/folder yang namanya diawali titik. Kalau hilang, buat manual lewat
**Add file > Create new file**, dengan nama `.github/workflows/build-apk.yml`,
lalu tempel isinya.

Setiap push ke `main` otomatis menjalankan unit test lalu membangun APK. APK
bisa diunduh dari tab **Actions**, pilih run terakhir, bagian **Artifacts**.

## Cara build lokal

Gradle wrapper sudah disertakan.

```bash
./gradlew :app:testDebugUnitTest   # 36 unit test
./gradlew :app:assembleDebug       # APK di app/build/outputs/apk/debug/
```

Atau buka folder ini di Android Studio (Ladybug / 2024.2.1 atau lebih baru).

Requirement:
- JDK 17
- Android SDK Platform 35
- NDK 27.0.12077973 + CMake 3.22.1 (untuk probe Vulkan native). Install lewat
  SDK Manager > SDK Tools > NDK (Side by side) dan CMake.

Tanpa NDK, build dengan `./gradlew assembleDebug -Ppixelrender.native=false`.
Aplikasi tetap jalan: `VulkanCapability` otomatis jatuh ke deteksi berbasis
`PackageManager` (versi + hardware level saja, tanpa daftar extension).

## Yang dideteksi

| Komponen | Sumber |
|---|---|
| Manufacturer, model, board, SoC, ABI, RAM | `android.os.Build`, `ActivityManager` |
| Resolusi, density, refresh rate + mode | `WindowManager`, `DisplayManager` |
| GPU vendor/renderer, GLES version, extension, limits | EGL14 + PBuffer context offscreen |
| Vulkan version, physical device, limits, extension | `dlopen("libvulkan.so")` via JNI |
| Vulkan system feature + hardware level | `PackageManager.systemAvailableFeatures` |
| Shizuku terpasang / berjalan / permission / UID | Shizuku API v13 |
| Free Fire, Free Fire MAX terpasang | `PackageManager` + `<queries>` |

## Capability matrix

Setiap kemampuan dinilai pada dua sumbu terpisah:

- **Device** - apakah GPU perangkat mendukungnya sama sekali.
- **Control** - apakah ada API Android yang sah untuk mengubahnya pada
  aplikasi lain (game).

`Device: SUPPORTED` + `Control: UNSUPPORTED` adalah hasil normal untuk texture
filtering, anisotropic, LOD, dan MSAA. Itu bukan bug dan bukan kegagalan
deteksi: state tersebut memang per-proses.

## Yang tidak dilakukan proyek ini

Tidak ada root, Magisk, perubahan SELinux/kernel/driver, modifikasi APK atau
file data game, memory editing, injeksi SO, hooking proses game, atau bypass
anti-cheat. Shizuku dipakai sebagai UID shell saja dan pada PHASE 1 belum
menjalankan satu perintah pun.


## PHASE 2 - Shizuku Manager

Yang dibuktikan di tahap ini, tanpa mengubah satu pun setting:

1. Permission Shizuku diminta dan statusnya terbaca benar.
2. User service (`PixelRenderUserService`) berhasil di-bind lewat
   `Shizuku.bindUserService` dan benar-benar berjalan sebagai UID 2000.
3. Command bisa dieksekusi, hasilnya kembali, dan semuanya tercatat di Logs.
4. `IWindowManager` bisa dibaca lewat binder, bukan parsing teks.

### Keamanan eksekusi command

`CommandPolicy` adalah satu-satunya tempat yang boleh meloloskan command:

- Command dikirim sebagai argv terpisah ke `ProcessBuilder`. Tidak pernah
  lewat shell, jadi tidak ada interpolasi yang bisa disalahgunakan.
- Hanya command yang cocok persis dengan allowlist yang lolos, termasuk
  jumlah argumen dan pola tiap argumen.
- Validasi dijalankan **dua kali**: di proses aplikasi sebelum menyeberangi
  binder, dan sekali lagi di dalam user service. Service tidak mempercayai
  proses aplikasi.
- Command bertanda `writes` ditolak selama `ACTIVE_PHASE < 4`.

Allowlist PHASE 2 (semuanya baca):

```
id
wm size
wm density
settings get <namespace> <key>
device_config get game_overlay <package>
getprop <key dari daftar tetap>
```

### Kenapa IWindowManager dibaca duluan

`getInitialDisplaySize` mengembalikan resolusi fisik panel, `getBaseDisplaySize`
mengembalikan resolusi yang sedang aktif. Kalau keduanya berbeda, berarti
perangkat **sudah** punya override resolusi sebelum aplikasi ini jalan.
Nilai yang aktif itulah yang disimpan sebagai baseline di `BackupStore`, bukan
resolusi panel. Tanpa ini, restore di PHASE 6 akan mengembalikan perangkat ke
nilai yang salah.

### Cara uji

1. Jalankan Shizuku, berikan permission di tab Shizuku.
2. Tekan **Bind service**. UID service harus muncul `2000`.
3. Tekan **Jalankan probe**. Command `id` harus mengembalikan `uid=2000(shell)`.
4. Buka tab Logs untuk melihat seluruh command beserta hasilnya.
5. Tekan **Restore**. Pada PHASE 2 tombol ini memang harus melaporkan tidak ada
   yang perlu dikembalikan.

Yang paling saya butuhkan dari hasil probe kamu: baris
`device_config get game_overlay com.dts.freefireth`. Itu yang menentukan apakah
render scale per-game mungkin di perangkatmu, atau hanya resolusi sistem.


## PHASE 3 - Backend abstraction

Capability matrix tidak lagi ditulis tangan di satu file. Sekarang setiap
backend menjawab sendiri untuk tiap parameter, lalu jawabannya digabung.

```
GraphicsBackend
 |- ShizukuBackend        priority 100   PRIVILEGED
 |- VulkanBackend         priority  30   DETECTION_ONLY
 |- OpenGLBackend         priority  20   DETECTION_ONLY
 |- StandardAndroidBackend priority 10   ASSISTED
 \- UnsupportedBackend    priority   0   fallback terakhir
```

### Aturan yang mengikat semua backend

Sebuah backend hanya boleh melaporkan `externalControl = SUPPORTED` kalau
backend itu punya jalur API nyata untuk mengubah parameter tersebut pada
aplikasi lain, **dan** jalur itu sudah terverifikasi pada perangkat yang
sedang berjalan. Kalau belum diverifikasi, jawabannya `UNKNOWN`.

Contoh konkret: `ShizukuBackend` baru menaikkan `DISPLAY_RESOLUTION` menjadi
`SUPPORTED` setelah `IWindowManager` benar-benar terbaca di PHASE 2. Sebelum
probe dijalankan, statusnya `UNKNOWN` walaupun Shizuku sudah terhubung.

### Cara penggabungan

Urutan peringkat saat beberapa backend berbeda pendapat:

```
SUPPORTED > PARTIALLY_SUPPORTED > UNKNOWN > UNSUPPORTED
```

`UNKNOWN` sengaja ditaruh di atas `UNSUPPORTED`. Kalau satu backend yakin
tidak bisa dan backend lain belum tahu, jawaban yang jujur adalah "belum
tahu", bukan "tidak bisa". Semua pendapat tetap disimpan dan ditampilkan di
tab Caps, jadi kesimpulan gabungan tidak pernah menutupi alasan aslinya.

### Peran backend

| Peran | Arti |
|---|---|
| `DETECTION_ONLY` | Hanya membaca kemampuan GPU. Tidak pernah mengubah apa pun. |
| `ASSISTED` | Tidak bisa mengubah sendiri, tapi mengantar user ke UI sistem. |
| `PRIVILEGED` | Bisa mengubah lewat binder dengan UID shell. |

`StandardAndroidBackend` berperan `ASSISTED` karena inilah jalur tanpa Shizuku
yang benar-benar ada: banyak OEM menyediakan pengaturan resolusi layar di
Settings > Display, dan menurunkannya memberi efek yang sama dengan yang
dikejar aplikasi ini. Aplikasi hanya membuka halamannya; yang mengubah tetap
user. Karena ketersediaannya berbeda per OEM dan tidak bisa dideteksi secara
andal, statusnya `UNKNOWN`, bukan `SUPPORTED`.

### Tab Backend

Menampilkan tiap backend beserta alasan tersedia atau tidak, dan parameter apa
yang dipegangnya. Backend yang tidak tersedia tidak bisa dipilih. Kalau backend
yang dipilih manual jadi tidak tersedia, aplikasi kembali ke Auto dan
mencatatnya di Logs.


## PHASE 4 - Kontrol resolusi render

Parameter yang diimplementasikan: **ukuran dan density display** lewat
`IWindowManager`. Ini satu-satunya parameter di matriks yang Control-nya bisa
`SUPPORTED`, dan satu-satunya yang benar-benar menurunkan jumlah piksel yang
dirender Free Fire. Density ikut turun sebanding, jadi layout dalam dp tidak
berubah; yang berkurang hanya piksel, lalu sistem meng-upscale ke panel dan
hasilnya terlihat kotak-kotak.

### Lapisan pengaman, berurutan

1. **Planner di aplikasi** (`DisplayPlanner`) menghitung target dari ukuran
   *panel*, bukan resolusi aktif. Rasio aspek dikunci, sisi terpendek minimal
   320 px, density minimal 100 dpi. Hanya langkah tetap 90/80/75/66/50/40%.
2. **Target restore ditulis ke disk sebelum apa pun disentuh**, dengan
   `commit()` yang sinkron. Kalau proses mati di tengah jalan, target restore
   sudah ada dan ditawarkan saat aplikasi dibuka lagi.
3. **Validasi ulang di dalam user service** (`DisplayWritePolicy`). Service
   tidak mempercayai aplikasi: menaikkan resolusi di atas panel, mengubah rasio
   aspek, atau turun di bawah 30% ditolak di sini walaupun aplikasi mengirimnya.
4. **Baca ulang setelah menulis.** `Applied` hanya dikembalikan kalau nilai
   yang terbaca sesudahnya sama persis dengan yang diminta. Kalau OEM diam-diam
   mengabaikan override, hasilnya dilaporkan sebagai gagal, bukan sukses.
5. **Rollback otomatis** kalau langkah mana pun gagal.
6. **Konfirmasi 15 detik.** Setelah berhasil, dialog "Pertahankan /
   Kembalikan" muncul dan tidak bisa ditutup dengan tap di luar. Diam berarti
   kembalikan. Resolusi yang salah bisa membuat layar sulit dipakai, jadi
   jalan pulangnya tidak boleh bergantung pada kemampuan user menekan tombol.

### Restore yang tidak merusak override milikmu

Target restore adalah state tepat sebelum PixelRender menulis pertama kali:

- Kalau sebelumnya **tidak ada** override, restore = `clearForcedDisplaySize`.
  Men-set ulang ke ukuran panel tidak sama: override tetap tercatat di sistem.
- Kalau sebelumnya **sudah ada** override (misalnya kamu pernah pakai
  `wm size` sendiri), restore men-set kembali ke nilai itu.

Apply berkali-kali tidak menimpa target restore. Kalau ditimpa, restore akan
"mengembalikan" ke hasil ubahan aplikasi sendiri dan tidak pernah benar-benar
pulang.

### Jalur darurat

Kalau Shizuku mati dan resolusi tertinggal dalam keadaan berubah:

```bash
adb shell wm size reset
adb shell wm density reset
```

Perhatikan bahwa ini selalu membersihkan override, jadi kalau sebelumnya kamu
punya override sendiri, nilainya perlu di-set ulang manual.

### Yang sengaja belum diimplementasikan

**Render scale per-game (Game Mode downscale).** API-nya ada di Android 13+,
tetapi sintaks `cmd game` berbeda antar versi Android dan penerapannya
bergantung OEM serta manifest game itu sendiri. Saya tidak mau mengirim
command tulis yang sintaksnya belum terverifikasi di perangkatmu, karena
command yang salah sintaks hanya mencetak usage lalu keluar: dari luar
terlihat "dijalankan", padahal tidak terjadi apa-apa. Probe sekarang membaca
`cmd game list-modes` dan `list-configs` untuk Free Fire; dari output itu jalur
tulisnya bisa dibuat dengan benar.

**Tidak ada command shell yang menulis.** Allowlist shell tetap hanya berisi
command baca. Semua penulisan lewat tiga method bertipe di user service yang
punya validasi sendiri.


## PHASE 5 - Profil

| Profil | Resolusi render | Filtering | Anisotropic | LOD | AA |
|---|---|---|---|---|---|
| Default | kembali ke state sebelum PixelRender menulis | - | - | - | - |
| Pixel Low | 75% panel | rendah | off | moderat | rendah / off |
| Pixel Medium | 50% panel | sangat rendah | off | lebih kuat | off |
| Pixel Extreme | 40% panel | minimum | off | agresif | off |
| Custom | 90 / 80 / 75 / 66 / 50 / 40% | - | - | - | - |

Pada konsep awal, penurunan resolusi hanya ada di Pixel Extreme. Di sini Low dan
Medium juga mendapatkannya, karena resolusi render adalah satu-satunya kolom
yang punya API nyata. Tanpa itu, Low dan Medium tidak akan mengubah apa pun.

### Kolom lain tetap dicantumkan, dengan status jujur

Setiap bagian profil dicocokkan dengan capability matrix sebelum Apply dan
diberi salah satu dari empat status:

| Status | Arti |
|---|---|
| Diterapkan | Ada jalur tulis terverifikasi dan akan dijalankan. |
| Tidak ada API | Android tidak menyediakan cara mengubahnya di aplikasi lain. |
| Belum terverifikasi | Mungkin bisa, tetapi belum dipastikan di perangkat ini. |
| Terhalang | Seharusnya bisa, tetapi ada syarat yang belum terpenuhi. |

Filtering, anisotropic, LOD, dan AA sengaja ditulis sebagai deskripsi
kualitatif, bukan angka. Menulis "LOD bias +2.0" untuk parameter yang tidak
punya API akan memberi kesan ada nilai yang benar-benar diterapkan.

### Tidak ada fallback diam-diam

Kalau skala sebuah profil tidak aman untuk panel perangkat (misalnya Pixel
Extreme 40% pada panel 720p menghasilkan sisi 288 px, di bawah batas 320 px),
profil itu ditandai **Terhalang** beserta alasannya. Profil tidak diganti ke
skala lain tanpa sepengetahuan user. Gunakan Custom untuk memilih langkah yang
aman.

### Status profil dibaca dari state nyata

Status di Home tidak hanya berasal dari catatan "profil X sudah diterapkan".
Setiap kali user service terhubung, state display dibaca ulang dan dicocokkan
dengan target profil:

- **Active**: display aktif sama persis dengan target profil.
- **Berubah di luar aplikasi**: ada catatan profil aktif, tetapi display aktif
  berbeda. Biasanya karena resolusi diubah lewat Settings atau `wm size`.
  RESTORE DEFAULT tetap aman dipakai.
- **Active (belum diverifikasi)**: tercatat aktif, tetapi service belum
  terhubung sehingga belum bisa dibaca ulang.

### Halaman

Navigasi sekarang mengikuti daftar halaman di spesifikasi awal: Home, Profil
(Graphics Profiles), Device Info, Advanced (backend + capability matrix),
Shizuku, serta Logs dan About di top bar.


## PHASE 6 - Backup dan pemulihan

Audit terhadap PHASE 4-5 menemukan tiga celah nyata:

1. **Janji konfirmasi hanya berlaku selama aplikasi hidup.** Hitung mundur
   15 detik ada di memori. Kalau layar jadi sulit dipakai lalu aplikasi
   di-force-close atau perangkat di-reboot, perubahan yang belum dikonfirmasi
   tertinggal selamanya.
2. **Timeout selalu kembali ke kondisi asli.** Kalau Pixel Low sudah
   dikonfirmasi lalu Pixel Extreme dicoba dan tidak dikonfirmasi, perangkat
   melompat ke resolusi asli, bukan kembali ke Pixel Low.
3. **Override bertahan setelah reboot, Shizuku mode ADB tidak.** User bisa
   terjebak dengan resolusi rendah tanpa tahu penyebabnya.

### Journal di disk

`ChangeJournal` mencatat status setiap perubahan dengan `commit()` sinkron:

```
NONE -> APPLYING -> AWAITING_CONFIRMATION -> CONFIRMED
            \______________\___________________> (kembali) -> NONE / CONFIRMED
```

Saat aplikasi dibuka, status dibaca dari disk, bukan dari memori. Perubahan
yang berhenti di `APPLYING` atau `AWAITING_CONFIRMATION` berarti tidak pernah
dikonfirmasi, dan dikembalikan otomatis begitu user service Shizuku
terhubung. Itu kontrak yang sama dengan dialog 15 detik, hanya sekarang
berlaku walaupun aplikasi sempat mati.

Perubahan berstatus `CONFIRMED` **tidak pernah** dikembalikan otomatis.

### Dua jenis "kembali"

| Aksi | Tujuan |
|---|---|
| Kembalikan (dialog, timeout, pemulihan otomatis) | Profil terkonfirmasi sebelumnya kalau ada, kalau tidak ke kondisi asli |
| RESTORE DEFAULT | Selalu ke kondisi sebelum PixelRender pernah menulis |

Kalau profil sebelumnya gagal diterapkan ulang, aplikasi turun ke kondisi asli,
bukan membiarkan state campuran. Kalau apply gagal di tengah jalan, aturan yang
sama berlaku.

### Setelah reboot

- `BootReceiver` membuat satu notifikasi kalau resolusi masih diubah. Receiver
  ini tidak pernah menulis ke perangkat.
- Izin notifikasi (Android 13+) diminta tepat saat kamu memilih Pertahankan,
  karena di situlah perubahan akan bertahan melewati reboot. Kalau ditolak,
  pengingat tetap muncul di dalam aplikasi.
- Reboot dideteksi lewat `Settings.Global.BOOT_COUNT`. Untuk profil yang sudah
  dikonfirmasi, aplikasi bertanya Pertahankan atau Kembalikan; tidak ada
  penulisan tanpa keputusan user.

### Shizuku hilang saat perubahan aktif

Home menampilkan kartu Pemulihan dengan status Shizuku dan jalur darurat
`adb`. Target restore tetap tersimpan sampai pemulihan berhasil; kegagalan
tidak pernah menghapusnya.

### Least privilege

User service hanya di-bind otomatis kalau ada perubahan yang perlu
diverifikasi atau dipulihkan. Di luar itu, proses privileged tidak dijalankan
sampai user menekan Bind service.

### Laporan verifikasi

Tab Shizuku menyimpan 20 operasi terakhir: jenis operasi, nilai yang diminta,
nilai yang benar-benar terbaca dari `IWindowManager`, dan hasilnya. Pembacaan
ulang dicoba hingga 3 kali dengan jeda 150 ms untuk OEM yang menerapkan
override sedikit terlambat; kalau tetap tidak cocok, hasilnya tetap gagal.

### Logika yang sudah diuji di luar perangkat

File yang tidak bergantung pada Android SDK dikompilasi dan diuji langsung
dengan kotlinc 2.0.21:

- `CommandPolicy`: 11 command baca lolos; 24 variasi berbahaya ditolak
  (bentuk tulis `wm size 540x1200`, `settings put`, injeksi `;`, `$(...)`,
  newline, `sh -c`, `su`, `cmd game set/mode`, `device_config put`); tidak ada
  kata tulis di allowlist.
- `DisplayWritePolicy` x `DisplayPlanner`: di 17 resolusi panel nyata
  (720p sampai 1440p, termasuk tablet landscape), seluruh 100 rencana yang
  ditandai "boleh" juga diterima validasi service, rasio aspek menyimpang
  < 1%, dimensi genap, tidak ada yang naik di atas panel.
- `ProfileResolver`: hanya resolusi yang bisa "Diterapkan"; klaim SUPPORTED
  palsu untuk parameter lain tetap tidak diterapkan; tidak ada fallback
  diam-diam untuk skala yang tidak aman.

Hasil: 607 pemeriksaan, 0 gagal.


## PHASE 7 - Pengujian

Rencana lengkap dan matriks perangkat ada di [TESTING.md](TESTING.md).

### Unit test

```bash
./gradlew :app:testDebugUnitTest
```

36 test JVM murni, dijalankan GitHub Actions **sebelum** APK dibuat. Kalau
salah satu jaminan keamanan rusak, APK tidak dibuat.

| Kelas | Isi |
|---|---|
| `CommandPolicyTest` | 11 command baca lolos, 24 variasi berbahaya ditolak, tidak ada kata tulis di allowlist |
| `DisplayWritePolicyTest` | Batas apply dan restore di sisi service |
| `DisplayPlannerTest` | Setiap skala yang ditawarkan diterima service, di 17 resolusi panel |
| `ProfileResolverTest` | Hanya resolusi yang bisa diterapkan; tidak ada fallback diam-diam |
| `CapabilityMergeTest` | Aturan penggabungan pendapat backend |
| `RecoveryPolicyTest` | Identik dengan logika PHASE 6 untuk semua 128 kombinasi masukan |
| `GpuClassifierTest` | String renderer nyata: Adreno, Mali, Immortalis, Xclipse (termasuk lewat ANGLE), PowerVR, Maleoon, emulator |
| `TestPlanTest` | Penilaian uji otomatis dan manual |

Test ini juga sudah diperiksa bisa gagal: memasukkan kembali bug, menukar
urutan prioritas pemulihan, atau menambahkan bentuk tulis ke allowlist
masing-masing membuat test merah.

### Di perangkat

Tab **Device** sekarang dimulai dengan hasil uji untuk perangkat itu: 10 uji
otomatis yang dinilai dari bukti yang benar-benar terbaca (bukan dari
anggapan), dan 3 uji manual yang kamu nilai sendiri. Tombol **Bagikan
laporan** membuat laporan teks berisi sel matriks, GPU, capability matrix,
output probe, riwayat verifikasi, hasil uji, dan log terakhir. Laporan tidak
berisi IMEI, nomor seri, akun, lokasi, atau daftar aplikasi.
# ProjectAPKs
