package com.quantumlock.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.quantumlock.security.UserPrincipal;
import com.quantumlock.service.DocumentService;
import com.quantumlock.service.DocumentService.Download;
import com.quantumlock.service.Dto.DocView;
import com.quantumlock.service.Dto.UploadReceipt;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private final DocumentService documents;

    public DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @GetMapping
    public List<DocView> list(@AuthenticationPrincipal UserPrincipal u) {
        return documents.list(u);
    }

    @GetMapping("/{id}")
    public DocView get(@AuthenticationPrincipal UserPrincipal u, @PathVariable UUID id) {
        return documents.get(u, id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadReceipt upload(@AuthenticationPrincipal UserPrincipal u, @RequestParam("file") MultipartFile file,
                                @RequestParam(value = "classification", defaultValue = "INTERNAL") String classification) throws IOException {
        return documents.upload(u, file.getOriginalFilename(), file.getContentType(), classification, file.getBytes());
    }

    @PostMapping(value = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadReceipt newVersion(@AuthenticationPrincipal UserPrincipal u, @PathVariable UUID id,
                                    @RequestParam("file") MultipartFile file) throws IOException {
        return documents.uploadNewVersion(u, id, file.getBytes());
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal UserPrincipal u, @PathVariable UUID id,
                                           @RequestParam(value = "version", required = false) Integer version,
                                           @RequestParam(value = "mode", defaultValue = "download") String mode) {
        Download dl = documents.download(u, id, version, "mode=" + mode);
        MediaType type;
        try {
            type = MediaType.parseMediaType(dl.doc().contentType);
        } catch (Exception e) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(dl.doc().name, StandardCharsets.UTF_8).build().toString())
                .header("X-Document-Version", String.valueOf(dl.version().version))
                .body(dl.plaintext());
    }

    @PostMapping("/{id}/tamper")
    public DocView tamper(@AuthenticationPrincipal UserPrincipal u, @PathVariable UUID id) {
        return documents.toggleTamper(u, id);
    }
}
