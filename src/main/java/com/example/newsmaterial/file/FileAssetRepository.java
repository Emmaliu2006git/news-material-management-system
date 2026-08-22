package com.example.newsmaterial.file;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface FileAssetRepository extends JpaRepository<FileAsset,UUID>{}
