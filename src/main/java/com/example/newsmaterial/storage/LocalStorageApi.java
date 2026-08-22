package com.example.newsmaterial.storage;

import com.example.newsmaterial.common.api.ApiException;
import com.example.newsmaterial.file.*;
import com.example.newsmaterial.security.AuthenticatedUser;
import com.example.newsmaterial.upload.UploadIntentService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @Profile("local") @RequestMapping("/api/v1/local-storage")
public class LocalStorageApi {
 private final UploadIntentService uploads; private final LocalStorageAdapter storage; private final FileAssetRepository files;
 public LocalStorageApi(UploadIntentService uploads,LocalStorageAdapter storage,FileAssetRepository files){this.uploads=uploads;this.storage=storage;this.files=files;}
 @PutMapping("/uploads/{intentId}") ResponseEntity<Void> put(@PathVariable UUID intentId,@AuthenticationPrincipal AuthenticatedUser user,@RequestHeader(HttpHeaders.CONTENT_TYPE) String mime,@RequestBody byte[] body){uploads.localPut(intentId,user.id(),mime,body,storage);return ResponseEntity.noContent().build();}
 @GetMapping("/files/{fileId}") ResponseEntity<byte[]> get(@PathVariable UUID fileId){FileAsset f=files.findById(fileId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"FILE_NOT_FOUND","File was not found"));return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.getMimeType())).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+f.getOriginalFilename().replace("\"","")+"\"").body(storage.read(f.getObjectKey()));}
}
