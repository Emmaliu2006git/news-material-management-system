package com.example.newsmaterial.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<ReportingProject, UUID> {
    List<ReportingProject> findAllByOrderByCreatedAtDesc();
}
