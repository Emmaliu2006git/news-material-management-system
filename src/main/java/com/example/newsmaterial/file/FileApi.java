package com.example.newsmaterial.file;

import com.example.newsmaterial.common.api.ApiException;
import com.example.newsmaterial.analysis.ImageAnalysisService;
import com.example.newsmaterial.storage.StoragePort;
import com.example.newsmaterial.security.AuthenticatedUser;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;import java.time.Duration;import java.util.UUID;

@RestController @RequestMapping("/api/v1/files")
public class FileApi {
 private final FileAssetRepository files;private final StoragePort storage;private final ImageAnalysisService analyses;
 public FileApi(FileAssetRepository files,StoragePort storage,ImageAnalysisService analyses){this.files=files;this.storage=storage;this.analyses=analyses;}
 @PostMapping("/{id}/analysis/retry") ImageAnalysisService.RetryResult retry(@PathVariable UUID id,@AuthenticationPrincipal AuthenticatedUser user){return analyses.retry(id,user.id(),Instant.now());}
 @PostMapping("/{id}/download-url") DownloadUrl download(@PathVariable UUID id){FileAsset f=files.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"FILE_NOT_FOUND","File was not found"));Instant expires=Instant.now().plus(Duration.ofMinutes(5));return new DownloadUrl(storage.createDownloadUrl(f.getId(),f.getObjectKey(),f.getOriginalFilename(),f.getMimeType(),expires),expires);}
 public record DownloadUrl(String downloadUrl,Instant expiresAt){}
}
