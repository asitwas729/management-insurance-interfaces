package com.example.interfacehub.presentation;

import com.example.interfacehub.application.scheduler.RetentionService;
import com.example.interfacehub.application.scheduler.RetentionService.RetentionResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final RetentionService retentionService;

    public AdminController(RetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @PostMapping("/archive")
    public RetentionResult triggerArchive() {
        return retentionService.archiveAndPurge();
    }
}
