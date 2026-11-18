package in.healthconnect.storage.web;

import in.healthconnect.portal.CurrentUserProvider;
import in.healthconnect.storage.dto.StoredFileResponse;
import in.healthconnect.storage.entity.StoredFile;
import in.healthconnect.storage.entity.enums.FileCategory;
import in.healthconnect.storage.service.FileService;
import in.healthconnect.wrapper.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// Generic file API: upload any supported file, and download one back if the category guard allows.
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;
    private final CurrentUserProvider currentUser;

    public FileController(FileService fileService, CurrentUserProvider currentUser) {
        this.fileService = fileService;
        this.currentUser = currentUser;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<StoredFileResponse>> upload(@RequestParam MultipartFile file,
                                                                  @RequestParam FileCategory category,
                                                                  @RequestParam(required = false) Integer referenceId) {
        StoredFileResponse response = fileService.upload(file, category, referenceId, currentUser.currentUser().getId());
        return ResponseEntity.ok(ApiResponse.success(response, "File uploaded"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> download(@PathVariable Integer id) {
        StoredFile file = fileService.requireReadable(id, currentUser.currentUser().getId());
        MediaType type = file.getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(file.getContentType());
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getOriginalFilename() + "\"")
                .body(fileService.bytes(file));
    }
}
