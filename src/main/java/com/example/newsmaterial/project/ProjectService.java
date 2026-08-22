package com.example.newsmaterial.project;

import com.example.newsmaterial.common.api.ApiException;
import com.example.newsmaterial.security.AppUser;
import com.example.newsmaterial.security.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {
    private final ProjectRepository projects;
    private final UserRepository users;

    public ProjectService(ProjectRepository projects, UserRepository users) {
        this.projects = projects;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list() {
        return projects.findAllByOrderByCreatedAtDesc().stream().map(ProjectResponse::from).toList();
    }

    @Transactional
    public ProjectResponse create(UUID ownerId, ProjectRequest request) {
        AppUser owner = users.findById(ownerId).orElseThrow();
        ReportingProject project = projects.save(new ReportingProject(UUID.randomUUID(), request.title().trim(), owner));
        return ProjectResponse.from(project);
    }

    @Transactional
    public ProjectResponse rename(UUID projectId, UUID requesterId, ProjectRequest request) {
        ReportingProject project = projects.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project was not found"));
        if (!project.getCreatedBy().getId().equals(requesterId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission for this action");
        }
        project.rename(request.title().trim());
        return ProjectResponse.from(project);
    }
}
