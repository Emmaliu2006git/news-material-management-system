package com.example.newsmaterial.storage;

import com.example.newsmaterial.common.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Component @Profile("local")
public class LocalStorageAdapter implements StoragePort {
    private final Path root;
    public LocalStorageAdapter(@Value("${app.storage.local-root:${java.io.tmpdir}/news-material-storage}") String root) {
        this.root=Path.of(root).toAbsolutePath().normalize();
        try { Files.createDirectories(this.root); } catch (IOException e) { throw new IllegalStateException(e); }
    }
    @Override public UploadTarget createUploadTarget(UUID intentId, String key, String mime, long size, String sha, Instant expires) {
        return new UploadTarget("/api/v1/local-storage/uploads/"+intentId, Map.of("Content-Type", mime));
    }
    @Override public StoredObject inspect(String key) {
        Path path=resolve(key);
        if (!Files.isRegularFile(path)) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"UPLOAD_NOT_VERIFIED","Uploaded object was not found");
        try { byte[] bytes=Files.readAllBytes(path); return new StoredObject(bytes.length, Files.readString(meta(key)), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))); }
        catch (Exception e) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"STORAGE_UNAVAILABLE","Storage could not be read"); }
    }
    @Override public String createDownloadUrl(UUID fileId, String key, String filename, String mime, Instant expires) { return "/api/v1/local-storage/files/"+fileId; }
    public void put(String key, String mime, byte[] bytes) {
        try { Path path=resolve(key); Files.createDirectories(path.getParent()); Files.write(path, bytes, StandardOpenOption.CREATE_NEW); Files.writeString(meta(key),mime,StandardOpenOption.CREATE_NEW); }
        catch (FileAlreadyExistsException e) { throw new ApiException(HttpStatus.CONFLICT,"UPLOAD_ALREADY_EXISTS","Upload target has already been used"); }
        catch (IOException e) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"STORAGE_UNAVAILABLE","Storage could not be written"); }
    }
    public byte[] read(String key) { try { return Files.readAllBytes(resolve(key)); } catch(IOException e){ throw new ApiException(HttpStatus.NOT_FOUND,"FILE_NOT_FOUND","File was not found"); } }
    private Path meta(String key){return resolve(key+".content-type");}
    private Path resolve(String key){ Path path=root.resolve(key).normalize(); if(!path.startsWith(root)) throw new IllegalArgumentException("Invalid object key"); return path; }
}
