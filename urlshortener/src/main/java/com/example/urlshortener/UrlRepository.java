package com.example.urlshortener;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UrlRepository extends JpaRepository<ShortUrl, Long> {
    // Interface allows for saving/finding data
}