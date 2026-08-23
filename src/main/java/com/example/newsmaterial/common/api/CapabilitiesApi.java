package com.example.newsmaterial.common.api;

import com.example.newsmaterial.file.FilePolicy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/capabilities")
public class CapabilitiesApi {
    private final FilePolicy policy;

    public CapabilitiesApi(FilePolicy policy) {
        this.policy = policy;
    }

    @GetMapping
    CapabilitiesView get() {
        return new CapabilitiesView(new UploadCapabilities(
                policy.maxFiles(), policy.maxFileBytes(), policy.maxTotalBytes()));
    }

    public record CapabilitiesView(UploadCapabilities upload) {
    }

    public record UploadCapabilities(int maxFiles, long maxFileBytes, long maxTotalBytes) {
    }
}
