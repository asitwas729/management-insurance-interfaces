package com.example.interfacehub.presentation;

import com.example.interfacehub.application.search.GlobalSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Global Search", description = "Unified search APIs for dashboard global search")
public class GlobalSearchController {

    private final GlobalSearchService globalSearchService;

    public GlobalSearchController(GlobalSearchService globalSearchService) {
        this.globalSearchService = globalSearchService;
    }

    @GetMapping("/search")
    @Operation(summary = "Global search", description = "Searches interfaces/configs/histories/retries by keyword")
    public GlobalSearchResponse search(
        @RequestParam String q,
        @RequestParam(defaultValue = "12") int limit
    ) {
        return globalSearchService.search(q, limit);
    }
}

