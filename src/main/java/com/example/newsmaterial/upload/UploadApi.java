package com.example.newsmaterial.upload;

import com.example.newsmaterial.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/v1/uploads")
public class UploadApi {
    private final UploadIntentService service; public UploadApi(UploadIntentService service){this.service=service;}
    @PostMapping("/intents") ResponseEntity<UploadIntentResponse> create(@AuthenticationPrincipal AuthenticatedUser user,@Valid @RequestBody UploadIntentRequest request){return ResponseEntity.status(HttpStatus.CREATED).body(new UploadIntentResponse(service.create(user.id(),request.files())));}
    public record UploadIntentRequest(List<UploadIntentService.UploadFileSpec> files){}
    public record UploadIntentResponse(List<UploadIntentService.IntentResult> files){}
}
