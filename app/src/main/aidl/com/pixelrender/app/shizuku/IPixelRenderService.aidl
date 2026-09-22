package com.pixelrender.app.shizuku;

// Compiler AIDL mewajibkan: nomor transaksi diberikan ke SEMUA method atau
// tidak sama sekali. destroy() wajib 16777114 (ditentukan Shizuku server),
// jadi semua method lain juga harus bernomor. Pola sama dengan contoh resmi
// Shizuku (IUserService.aidl). Nomor yang sudah dirilis jangan diubah; method
// baru ditambah dengan nomor berikutnya.
interface IPixelRenderService {
    void destroy() = 16777114;

    /** UID tempat service ini benar-benar berjalan. 2000 = shell. */
    int getServiceUid() = 1;

    /** Versi policy yang dikompilasi ke dalam service, untuk deteksi APK campuran. */
    int getPolicyVersion() = 2;

    /** Menjalankan command baca yang sudah lolos validasi. Mengembalikan JSON. */
    String execJson(in String[] command, int timeoutMs) = 3;

    /** Membaca ukuran/density display lewat IWindowManager. Mengembalikan JSON. */
    String readDisplayStateJson(int displayId) = 4;

    // Satu-satunya jalur tulis. Divalidasi ulang di dalam service, lalu state
    // dibaca ulang dan dikembalikan bersama state sebelumnya.

    String setDisplaySizeJson(int displayId, int width, int height, boolean restoring) = 5;

    String setDisplayDensityJson(int displayId, int density, boolean restoring) = 6;

    String clearDisplayOverrideJson(int displayId, boolean clearSize, boolean clearDensity) = 7;
}
