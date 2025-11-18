package com.mentoai.mentoai.service;

import com.mentoai.mentoai.service.crawler.ExternalActivity;
import com.mentoai.mentoai.service.crawler.ExternalActivityCrawler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 외부 크롤러들을 관리하는 팩토리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalCrawlerService {

    private final List<ExternalActivityCrawler> crawlers;

    /**
     * 소스 이름으로 크롤러 조회
     */
    public ExternalActivityCrawler getCrawler(String source) {
        return crawlers.stream()
                .filter(crawler -> crawler.getSourceName().equalsIgnoreCase(source))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown crawler source: " + source));
    }

    /**
     * 특정 소스에서 전체 활동 크롤링
     */
    public List<ExternalActivity> crawlAll(String source) {
        ExternalActivityCrawler crawler = getCrawler(source);
        log.info("Crawling all activities from source: {}", source);
        return crawler.crawlAll();
    }

    /**
     * 특정 소스에서 최신 활동 크롤링
     */
    public List<ExternalActivity> crawlRecent(String source) {
        ExternalActivityCrawler crawler = getCrawler(source);
        log.info("Crawling recent activities from source: {}", source);
        return crawler.crawlRecent();
    }

    /**
     * 사용 가능한 크롤러 소스 목록 조회
     */
    public List<String> getAvailableSources() {
        return crawlers.stream()
                .map(ExternalActivityCrawler::getSourceName)
                .toList();
    }
}


