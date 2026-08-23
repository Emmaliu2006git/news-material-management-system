package com.example.newsmaterial.file;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;import java.util.UUID;
public interface VersionFileRepository extends JpaRepository<VersionFile,VersionFileId>{
 List<VersionFile> findByVersion_IdOrderByDisplayOrder(UUID versionId);
 boolean existsByVersion_IdAndFile_Id(UUID versionId,UUID fileId);
}
