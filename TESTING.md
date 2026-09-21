# PixelRender - Rencana Pengujian (PHASE 7)

## Apa yang dibuktikan di mana

| Lapisan | Dijalankan di | Membuktikan |
|---|---|---|
| Unit test JVM (36 test) | GitHub Actions, sebelum APK dibuat | Allowlist menolak semua bentuk tulis; batas tulis service; setiap skala yang ditawarkan aplikasi diterima service di 17 resolusi panel; profil tidak pernah memalsukan status; logika pemulihan identik dengan PHASE 6 untuk semua 128 kombinasi masukan; klasifikasi GPU |
| Uji otomatis di aplikasi | HP nyata, tab Device | Deteksi GPU/GL/Vulkan, Shizuku berjalan sebagai shell, probe dan IWindowManager terbaca, apply/kembalikan/restore/pemulihan terverifikasi dari pembacaan ulang |
| Uji manual | HP nyata, dinilai olehmu | Hasil visual di Free Fire, pengingat setelah reboot, deteksi perubahan dari luar aplikasi |

Unit test tidak bisa menggantikan HP nyata: perilaku driver GPU, pembatasan
OEM terhadap override resolusi, dan Shizuku hanya bisa diamati di perangkat.

## Matriks

Isi setiap sel dengan laporan dari tombol **Bagikan laporan** (tab Device).
Sel matriks ditentukan otomatis oleh aplikasi dan tertulis di baris kedua
laporan, misalnya `Sel matriks: ARM Mali, dengan Shizuku`.

| GPU | Tanpa Shizuku | Shizuku (ADB) |
|---|---|---|
| Qualcomm Adreno | | |
| ARM Mali | | |
| ARM Immortalis | | |
| Samsung Xclipse | | |
| Imagination PowerVR | | |
| Lainnya (Maleoon, dll.) | | |

Sel minimum yang paling penting: keempat GPU utama dengan Shizuku, ditambah
minimal satu perangkat apa pun tanpa Shizuku.

## Prosedur per perangkat (sekitar 10 menit)

1. Pasang APK, buka tab **Device**. Uji otomatis deteksi terisi sendiri.
2. **Tanpa Shizuku dulu.** `fallback.noshizuku` harus Lulus: aplikasi jalan
   dan APPLY ditahan dengan alasan. Coba pintasan Settings di tab Advanced.
3. Jalankan Shizuku. Tab Shizuku: Grant Permission, Bind service, Jalankan probe.
4. Home: pilih Pixel Medium, APPLY PROFILE, tekan **Kembalikan** di dialog.
5. APPLY lagi, **Pertahankan**, buka Free Fire, nilai `apply.visual`.
6. APPLY profil lain, lalu **force-close aplikasi** sebelum 15 detik habis.
   Buka lagi dengan Shizuku aktif: harus dikembalikan otomatis.
7. Pertahankan sebuah profil, reboot. Periksa notifikasi dan kartu Pemulihan,
   nilai `recovery.reboot`.
8. Saat profil aktif, ubah resolusi dari luar (Settings atau
   `adb shell wm size 720x1600`). Home harus menampilkan
   "Berubah di luar aplikasi"; nilai `drift.detect`.
9. RESTORE DEFAULT. Kalau langkah 8 memakai `wm size`, jalankan
   `adb shell wm size reset` dulu supaya perangkat bersih.
10. Tab Device: **Bagikan laporan**.

## Yang perlu diperhatikan

Ini daftar hal untuk diperiksa, bukan klaim tentang perilaku tiap GPU.

- **Xclipse:** GLES bisa berjalan lewat ANGLE. Laporan menandainya sebagai
  "GLES lewat ANGLE"; periksa apakah probe GL tetap berjalan.
- **Mali dan Immortalis** memakai vendor ID Vulkan yang sama (0x13B5).
  Periksa apakah keluarga GPU di laporan sudah benar.
- **PowerVR** banyak ada di perangkat murah dengan Android lama. Periksa apakah
  probe Vulkan native berjalan.
- **OEM yang membatasi override resolusi.** Kalau nilai yang dibaca ulang
  tidak sama dengan yang ditulis, APPLY dilaporkan gagal dan otomatis
  dikembalikan. Itu perilaku yang benar, bukan bug; laporkan saja perangkatnya.
- **Shizuku mode root** (uid 0): PixelRender sengaja tidak memakainya karena
  proyek ini tidak menjalankan apa pun sebagai root. `shizuku.connect` akan
  berstatus "Tidak berlaku".

## Template hasil

```
Perangkat  :
SoC / GPU  :
Android    :
Sel matriks:
Ringkasan  : Lulus _ / Gagal _ / Belum _ / Tidak berlaku _
Catatan    :
(laporan diagnostik lengkap di bawah)
```
