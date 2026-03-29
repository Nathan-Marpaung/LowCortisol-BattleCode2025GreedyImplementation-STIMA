## Low Cortisol — Battlecode 2025 Greedy Bots

| NIM | Nama |
|---|---|
| 13524052 | Raynard Fausta |
| 13524062 | Nathan Edward Christoper Marpaung |
| 13524112 | Richard Samuel Simanullang |

## Deskripsi
Repositori ini berisi implementasi tiga bot Battlecode 2025 yang dikembangkan pada tugas besar **IF2211 Strategi Algoritma**, yaitu **Congok**, **TowerAndExplore**, dan **Introvert**. Ketiganya dibangun dengan pendekatan **algoritma greedy**. Tujuan utama permainan adalah menguasai **lebih dari 70% wilayah yang dapat dicat** sehingga seluruh heuristik bot diarahkan untuk mendukung objektif tersebut.

---

## Penjelasan Singkat Algoritma Greedy pada Setiap Bot

### 1. Congok (Main Bot)
Bot **Congok** menggunakan greedy yang berfokus pada **dominasi wilayah secara aman dan efisien**. Setiap unit memilih aksi lokal dengan keuntungan wilayah tertinggi pada giliran saat itu. Soldier memprioritaskan `ruins` aman untuk dibangun menjadi tower, lalu mengecat petak non-sekutu terbaik bila tidak sedang mengerjakan tower. Splasher difokuskan untuk mengecat area seluas mungkin dengan memilih titik `splash` terbaik. Tower memproduksi unit berdasarkan siklus tetap yang didominasi Soldier dan Splasher, lalu memilih lokasi spawn dengan skor keamanan tertinggi. Pendekatan ini membuat Congok stabil dalam ekspansi, efisien dalam penggunaan paint, dan tetap seimbang dalam pengelolaan sumber daya.

### 2. Introvert (Alternative Bots 1)
Bot **Introvert** menggunakan greedy yang berfokus pada **penguasaan wilayah secara bertahap sambil meminimalkan pemborosan paint**. Soldier memilih `ruins` terbaik berdasarkan skor, mengecat tile terbaik, lalu melakukan eksplorasi berbobot ke wilayah yang menjanjikan. Splasher menjadi ujung tombak ekspansi area dengan memilih target `splash` yang memberi hasil terbesar. Tower memproduksi unit berdasarkan skor kebutuhan lapangan saat itu. Strategi ini relatif aman dan terkontrol, tetapi pada beberapa kondisi masih terlalu bergantung pada Soldier untuk membangun tempo permainan.

### 3. TowerAndExplore (Alternative Bots 2)
Bot **TowerAndExplore** menggunakan greedy yang berfokus pada **akselerasi eksplorasi dan pembangunan tower secepat mungkin**, terutama pada fase awal permainan. Soldier diprioritaskan untuk mencari `ruins`, menandai pola, dan membangun tower. Setelah `ruins` berkurang, unit akan beralih ke eksplorasi wilayah kosong dan wilayah musuh. Tower menggunakan pola spawn siklis untuk memperbanyak unit sedini mungkin. Strategi ini kuat pada fase awal ketika banyak `ruins` tersedia, tetapi efektivitasnya menurun pada peta besar atau saat pathfinding tidak cukup baik.

Dari ketiga bot yang dibuat, strategi utama adalah **Congok** karena paling konsisten mengarah ke objektif permainan dan menunjukkan hasil pengujian terbaik.

### Alasan Pemilihan
- Heuristik greedy Congok paling selaras dengan objektif permainan.
- Seluruh unit diprogram untuk mendukung dominasi wilayah dari awal hingga akhir permainan.
- Hasil uji permainan menunjukkan Congok lebih unggul dibanding bot lain.

### Hasil Uji
- **Congok vs TowerAndExplore:** 62–13
- **Congok vs Introvert:** 44–31

---

## Requirement Program dan Instalasi Tertentu Bila Ada
Program ini memerlukan:
- **Java**
- **engine / library Battlecode 2025**
- **template atau scaffold proyek Battlecode 2025**
- **Gradle**, jika menggunakan template Battlecode berbasis Gradle

### Instalasi
1. Instal **Java Development Kit (JDK)**.
2. Siapkan **template Battlecode 2025** yang digunakan untuk kompetisi atau praktikum.
3. Tambahkan file bot ke dalam source folder proyek Battlecode.
4. Pastikan library `battlecode.common.*` sudah tersedia pada proyek.
5. Jika menggunakan Gradle, pastikan `gradlew` dan file build template tersedia.

---

## Command atau Langkah-Langkah Compile / Build Program

1. Clone repositori ini
\
```
git clone https://github.com/Nathan-Marpaung/LowCortisol-BattleCode2025GreedyImplementation-STIMA.git
```
3. Bot akan di develop pada directory tersebut
4. Jalankan perintah berikut
\
```
./gradlew build
cd client
```
6. Buka aplikasi yang ada di client
7. Pada tab queue pilih direktori LowCortisol-BattleCode2025GreedyImplementation-STIMA
8. Ketiga bot dapat anda mainkan pada 75 pilihan map yang ada
