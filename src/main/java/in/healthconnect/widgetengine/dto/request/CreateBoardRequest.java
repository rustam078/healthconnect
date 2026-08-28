package in.healthconnect.widgetengine.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

// To create a board you only need a name (it starts empty).
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateBoardRequest {

    @NotBlank
    @Size(max = 200)
    private String name;
}
