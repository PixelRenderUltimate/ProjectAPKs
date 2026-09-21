package com.pixelrender.app.shizuku;

interface IPixelRenderService {
    // Transaction id wajib untuk Shizuku user service.
    void destroy() = 16777114;

    /** UID tempat service ini benar-benar berjalan. 2000 = shell. */
    int getServiceUid();

    /** Versi policy yang dikompilasi ke dalam service, untuk deteksi APK campuran. */
    int getPolicyVersion();

    /** Menjalankan command baca yang sudah lolos validasi. Mengembalikan JSON. */
    String execJson(in String[] command, int timeoutMs);

    /** Membaca ukuran/density display lewat IWindowManager. Mengembalikan JSON. */
    String readDisplayStateJson(int displayId);

    // --- PHASE 4: satu-satunya jalur tulis. Divalidasi ulang di dalam service,
    //     lalu state dibaca ulang dan dikembalikan bersama state sebelumnya. ---

    String setDisplaySizeJson(int displayId, int width, int height, boolean restoring);

    String setDisplayDensityJson(int displayId, int density, boolean restoring);

    String clearDisplayOverrideJson(int displayId, boolean clearSize, boolean clearDensity);
}
