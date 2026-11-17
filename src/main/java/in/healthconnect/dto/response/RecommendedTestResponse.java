package in.healthconnect.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendedTestResponse {

    private Integer id;
    private String testName;
    private String instructions;
}
