package in.healthconnect.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendedTestRequest {

    @NotBlank(message = "Test name is required")
    @Size(max = 150)
    private String testName;

    @Size(max = 255)
    private String instructions;
}
