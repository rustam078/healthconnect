package in.healthconnect.dto.response;

import in.healthconnect.entity.LabTest;
import in.healthconnect.entity.enums.LabTestCategory;

// A catalogue test the doctor can order.
public record LabTestResponse(Integer id, String name, LabTestCategory category) {
    public static LabTestResponse from(LabTest test) {
        return new LabTestResponse(test.getId(), test.getName(), test.getCategory());
    }
}
