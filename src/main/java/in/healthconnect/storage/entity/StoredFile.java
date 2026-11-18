package in.healthconnect.storage.entity;

import in.healthconnect.entity.BaseEntity;
import in.healthconnect.storage.entity.enums.FileCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// A file kept on local disk; the row holds only a reference (storage_key), never the bytes.
// category + reference_id say what it is for and which record it belongs to.
@Entity
@Table(name = "stored_files")
@Getter
@Setter
@NoArgsConstructor
public class StoredFile extends BaseEntity {

    @Column(name = "storage_key", nullable = false, unique = true, length = 255)
    private String storageKey;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private FileCategory category;

    @Column(name = "reference_id")
    private Integer referenceId;

    @Column(name = "uploaded_by", nullable = false)
    private Integer uploadedBy;
}
