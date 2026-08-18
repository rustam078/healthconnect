package in.mustafa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

 @Id
 @GeneratedValue(strategy = GenerationType.IDENTITY)
 private Integer id;

 @CreatedDate
 @Column(name = "created_at", updatable = false)
 private Instant createdAt;

 @LastModifiedDate
 @Column(name = "updated_at")
 private Instant updatedAt;

 @Column(name = "is_deleted", nullable = false)
 private Boolean deleted = false;
}