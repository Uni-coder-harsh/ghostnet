package com.ghostnet.files;

import com.ghostnet.security.RequireAuth;
import com.ghostnet.security.SecurityContext;
import com.ghostnet.user.UserRecord;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * REST controller for conversation file uploads, secure streaming downloads, and previews (Req Section 4).
 */
@RestController
@RequestMapping("/api/files")
@RequireAuth
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentDto> uploadAttachment(
            @RequestParam("file") MultipartFile file,
            @RequestParam("conversationType") String conversationType,
            @RequestParam("conversationId") String conversationId) {
        UserRecord current = SecurityContext.requireUser();
        AttachmentDto dto = attachmentService.uploadAttachment(current, file, conversationType, conversationId);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable String id) {
        UserRecord current = SecurityContext.requireUser();
        AttachmentService.DownloadResource res = attachmentService.getAttachmentForDownload(current, id);

        String encodedFilename = URLEncoder.encode(res.filename(), StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(res.mimeType()))
                .contentLength(res.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + res.filename() + "\"; filename*=UTF-8''" + encodedFilename)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header("X-Content-Type-Options", "nosniff")
                .body(res.resource());
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<Resource> previewAttachment(@PathVariable String id) {
        UserRecord current = SecurityContext.requireUser();
        AttachmentService.DownloadResource res = attachmentService.getAttachmentForPreview(current, id);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(res.mimeType()))
                .contentLength(res.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + res.filename() + "\"")
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox")
                .header("X-Content-Type-Options", "nosniff")
                .body(res.resource());
    }

    @GetMapping("/{id}/info")
    public ResponseEntity<AttachmentDto> getAttachmentInfo(@PathVariable String id) {
        UserRecord current = SecurityContext.requireUser();
        AttachmentDto dto = attachmentService.getAttachmentInfo(current, id);
        return ResponseEntity.ok(dto);
    }
}
