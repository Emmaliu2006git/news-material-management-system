package com.example.newsmaterial.search;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/materials/search")
public class GlobalSearchApi {
    private final SearchService search;

    public GlobalSearchApi(SearchService search) {
        this.search = search;
    }

    @GetMapping
    List<SearchService.SearchResult> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "ALL") SearchFileType fileType,
            @RequestParam(required = false) Instant eventFrom,
            @RequestParam(required = false) Instant eventTo,
            @RequestParam(required = false) String location) {
        return search.globalSearch(new SearchService.GlobalSearchFilters(
                q, fileType, eventFrom, eventTo, location));
    }
}
