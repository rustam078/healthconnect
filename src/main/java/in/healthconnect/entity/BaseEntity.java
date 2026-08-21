package in.healthconnect.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("is_deleted = false")//Normal Hibernate queries mein is_deleted = true wale patients ko automatically exclude kar dena.
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