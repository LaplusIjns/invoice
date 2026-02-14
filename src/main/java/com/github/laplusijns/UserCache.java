package com.github.laplusijns;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class UserCache {
    private final Cache<String, UserData> fileCache;

    public UserCache() {
        this.fileCache = Caffeine.newBuilder()
                .expireAfterWrite(24, TimeUnit.HOURS)
                .maximumSize(Integer.MAX_VALUE)
                .build();
        super();
    }

    public void put(final String uuid, final UserData data) {
        fileCache.put(uuid, data);
    }

    public @Nullable UserData get(final String uuid) {
        return fileCache.getIfPresent(uuid);
    }

    public void delete(final String uuid) {
        fileCache.invalidate(uuid);
    }
}
