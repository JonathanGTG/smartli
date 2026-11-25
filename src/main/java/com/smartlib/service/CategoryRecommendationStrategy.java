package com.smartlib.service;

import com.smartlib.interfaces.RecommendationStrategy;
import com.smartlib.model.Category;
import com.smartlib.model.LibraryItem;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Penerapan Design Pattern: Strategy Pattern untuk Rekomendasi Buku.
 * Merekomendasikan buku berdasarkan kesamaan kategori yang diminati anggota.
 */
public class CategoryRecommendationStrategy implements RecommendationStrategy {

    @Override
    public List<LibraryItem> recommend(List<? extends LibraryItem> candidates,
                                       Category preferredCategory, int limit) {
        if (candidates == null || candidates.isEmpty() || preferredCategory == null) {
            return Collections.emptyList();
        }

        return candidates.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getCategory() != null &&
                        item.getCategory().getId() == preferredCategory.getId())
                .limit(Math.max(1, limit))
                .collect(Collectors.toList());
    }
}