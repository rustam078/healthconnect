package in.healthconnect.community.entity;

import in.healthconnect.community.entity.enums.CommunityVisibility;
import in.healthconnect.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// A chat group. Its visibility decides who can discover and join it; the creator is its first admin.
@Entity
@Table(name = "communities")
@Getter
@Setter
@NoArgsConstructor
public class Community extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommunityVisibility visibility;

    @Column(name = "created_by", nullable = false)
    private Integer createdBy;

    public Community(String name, String description, CommunityVisibility visibility, Integer createdBy) {
        this.name = name;
        this.description = description;
        this.visibility = visibility;
        this.createdBy = createdBy;
    }
}
