package com.smartlib.interfaces;

import com.smartlib.model.Category;
import com.smartlib.model.LibraryItem;
import java.util.List;


@FunctionalInterface
public interface RecommendationStrategy {


    List<LibraryItem> recommend(List<? extends LibraryItem> candidates,
                                Category preferredCategory, int limit);
}
