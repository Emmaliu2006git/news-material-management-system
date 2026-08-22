package com.example.newsmaterial.file;
import com.example.newsmaterial.material.domain.MaterialVersion;
import jakarta.persistence.*;
@Entity @Table(name="version_files")
public class VersionFile {
 @EmbeddedId private VersionFileId id;
 @ManyToOne(fetch=FetchType.LAZY) @MapsId("versionId") @JoinColumn(name="version_id") private MaterialVersion version;
 @ManyToOne(fetch=FetchType.LAZY) @MapsId("fileId") @JoinColumn(name="file_id") private FileAsset file;
 @Column(name="display_order",nullable=false,updatable=false) private int displayOrder;
 protected VersionFile(){}
 public VersionFile(MaterialVersion version,FileAsset file,int displayOrder){this.id=new VersionFileId(version.getId(),file.getId());this.version=version;this.file=file;this.displayOrder=displayOrder;}
 public FileAsset getFile(){return file;} public int getDisplayOrder(){return displayOrder;}
}
