package com.example.newsmaterial.project;

import com.example.newsmaterial.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectApi {
    private final ProjectService projects;

    public ProjectApi(ProjectService projects) {
        this.projects = projects;
    }

    @GetMapping
    List<ProjectResponse> list() {
        return projects.list();
    }

    @PostMapping
    ResponseEntity<ProjectResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                           @Valid @RequestBody ProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projects.create(user.id(), request));
    }

    @PatchMapping("/{id}")
    ProjectResponse rename(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user,
                           @Valid @RequestBody ProjectRequest request) {
        return projects.rename(id, user.id(), request);
    }
}
