package com.example.urlshortener;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.net.URI;
import java.util.Map;

@RestController
public class UrlController {

    private final UrlRepository repository;
    private static final String BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    public UrlController(UrlRepository repository) {
        this.repository = repository;
    }

    // 1. Create Short URL
    @PostMapping("/shorten")
    public String shortenUrl(@RequestBody String originalUrl) {
        ShortUrl url = new ShortUrl(originalUrl);
        ShortUrl saved = repository.save(url);
        return encode(saved.getId());
    }

    // 2. Redirect (With Analytics)
    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        Long id = decode(shortCode);
        return repository.findById(id)
                .map(url -> {
                    url.setClicks(url.getClicks() + 1);
                    repository.save(url);
                    return ResponseEntity.status(302).location(URI.create(url.getOriginalUrl())).<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // 3. Stats (FIXED: Added Type Hint <String, Object>)
    @GetMapping("/stats/{shortCode}")
    public ResponseEntity<Map<String, Object>> getStats(@PathVariable String shortCode) {
        Long id = decode(shortCode);
        return repository.findById(id)
                .map(url -> ResponseEntity.ok(Map.<String, Object>of(
                        "originalUrl", url.getOriginalUrl(),
                        "clicks", url.getClicks()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    // --- Helpers ---
    private String encode(long value) {
        StringBuilder sb = new StringBuilder();
        if (value == 0) return String.valueOf(BASE62.charAt(0));
        while (value > 0) {
            sb.append(BASE62.charAt((int) (value % 62)));
            value /= 62;
        }
        return sb.reverse().toString();
    }

    private Long decode(String str) {
        long result = 0;
        for (int i = 0; i < str.length(); i++) {
            result = result * 62 + BASE62.indexOf(str.charAt(i));
        }
        return result;
    }
}