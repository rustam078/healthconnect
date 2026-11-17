package in.healthconnect.dto.response;

import in.healthconnect.entity.OrderedTest;
import in.healthconnect.entity.enums.LabTestCategory;
import in.healthconnect.entity.enums.OrderedTestStatus;

// A test ordered on a visit, with its status and (once uploaded) its report id.
public record OrderedTestResponse(Integer id, String testName, LabTestCategory category,
                                  OrderedTestStatus status, Integer reportId) {
    public static OrderedTestResponse from(OrderedTest test) {
        return new OrderedTestResponse(test.getId(), test.getTestName(), test.getCategory(),
                test.getStatus(), test.getReportId());
    }
}
