package in.healthconnect.dto.request;

import in.healthconnect.entity.enums.Gender;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DoctorFilterDto {

    private Gender gender;
    private String qualification;
    private Integer minExperience;
    private Integer maxExperience;
    private BigDecimal minConsultationFee;
    private BigDecimal maxConsultationFee;
}
