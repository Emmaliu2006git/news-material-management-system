package com.example.newsmaterial.search;

import com.example.newsmaterial.material.domain.MaterialType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/materials")
public class SearchApi {
    private final SearchService search;

    public SearchApi(SearchService search) {
        this.search = search;
    }

    @GetMapping
    List<SearchService.SearchResult> search(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) MaterialType materialType,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo,
            @RequestParam(required = false) Instant eventFrom,
            @RequestParam(required = false) Instant eventTo,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String tag) {
        return search.search(projectId, new SearchService.SearchFilters(
                q, materialType, ownerId, createdFrom, createdTo, eventFrom, eventTo, location, tag));
    }
}
