package com.example.urlshortener;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.data.redis.core.StringRedisTemplate; // Import Redis
import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
public class UrlController {

    private final UrlRepository repository;
    private final StringRedisTemplate redis; // Redis Connector
    private static final String BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    // Constructor Injection for both Repo and Redis
    public UrlController(UrlRepository repository, StringRedisTemplate redis) {
        this.repository = repository;
        this.redis = redis;
    }

    // 1. Create Short URL
    @PostMapping("/shorten")
    public String shortenUrl(@RequestBody String originalUrl) {
        ShortUrl url = new ShortUrl(originalUrl);
        ShortUrl saved = repository.save(url);
        return encode(saved.getId());
    }

    // 2. Redirect (With Redis Caching)
    @GetMapping("/{shortCode:[a-zA-Z0-9]+}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        // A. Check Redis First (Fast!)
        String cachedUrl = redis.opsForValue().get(shortCode);
        
        if (cachedUrl != null) {
            // Found in cache! Redirect immediately (and async update clicks if you wanted)
            return ResponseEntity.status(302).location(URI.create(cachedUrl)).build();
        }

        // B. Not in Cache? Check Database (Slower)
        Long id = decode(shortCode);
        return repository.findById(id)
                .map(url -> {
                    // C. Save to Redis for next time (Expire in 10 minutes)
                    redis.opsForValue().set(shortCode, url.getOriginalUrl(), 10, TimeUnit.MINUTES);
                    
                    // Update stats (DB write still happens)
                    url.setClicks(url.getClicks() + 1);
                    repository.save(url);
                    
                    return ResponseEntity.status(302).location(URI.create(url.getOriginalUrl())).<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // 3. Stats
    @GetMapping("/stats/{shortCode:[a-zA-Z0-9]+}")
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