package com.smartlib.interfaces;

import com.smartlib.model.Category;
import com.smartlib.model.LibraryItem;
import java.util.List;

/**
 * Strategy untuk memilih buku yang direkomendasikan.
 * Pemanggil hanya bergantung pada interface ini, sehingga metodenya dapat diganti
 * (berdasarkan kategori, riwayat pinjam, dan seterusnya) tanpa mengubah kode pemanggil.
 */
@FunctionalInterface
public interface RecommendationStrategy {

    /**
     * @param candidates        koleksi yang boleh direkomendasikan
     * @param preferredCategory kategori yang diminati anggota, boleh null
     * @param limit             jumlah maksimal hasil, harus positif
     * @return daftar rekomendasi berukuran paling banyak {@code limit}
     */
    List<LibraryItem> recommend(List<? extends LibraryItem> candidates,
                                Category preferredCategory, int limit);
}
