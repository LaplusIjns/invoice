package com.github.laplusijns;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ImagRestController {
    ImageCache imageCache;

    public ImagRestController(final ImageCache imageCache) {
        super();
        this.imageCache = imageCache;
    }

    @GetMapping("/blob/{uuid}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable final String uuid) {
        final byte[] file = imageCache.get(uuid);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CACHE_CONTROL, "max-age=86400" + ", immutable")
                .body(file);
    }

    @GetMapping("/thumbnail/{uuid}")
    public ResponseEntity<byte[]> downloadThumbnailFile(@PathVariable final String uuid) {
        final byte[] file = imageCache.getThumbnail(uuid);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CACHE_CONTROL, "max-age=86400" + ", immutable")
                .body(file);
    }
}
