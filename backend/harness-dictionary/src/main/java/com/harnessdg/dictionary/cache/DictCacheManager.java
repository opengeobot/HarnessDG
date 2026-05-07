package com.harnessdg.dictionary.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.harnessdg.model.dict.dto.DictItemDTO;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class DictCacheManager {

    private final Cache<String, List<DictItemDTO>> itemCache;

    public DictCacheManager() {
        this.itemCache = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(Duration.ofMinutes(30))
                .build();
    }

    public List<DictItemDTO> getItems(String groupCode) {
        return itemCache.getIfPresent(groupCode);
    }

    public void putItems(String groupCode, List<DictItemDTO> items) {
        itemCache.put(groupCode, items);
    }

    public void evictGroup(String groupCode) {
        itemCache.invalidate(groupCode);
    }

    public void evictAll() {
        itemCache.invalidateAll();
    }
}
