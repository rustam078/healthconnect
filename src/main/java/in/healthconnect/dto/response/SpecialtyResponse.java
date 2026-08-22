package in.healthconnect.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpecialtyResponse {
private Integer id;
private String name;
private String description;
private LocalDateTime createdAt;
private LocalDateTime updatedAt;
}