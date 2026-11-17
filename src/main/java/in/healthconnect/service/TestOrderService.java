package in.healthconnect.service;

import in.healthconnect.dto.response.LabTestResponse;
import in.healthconnect.dto.response.OrderedTestResponse;
import in.healthconnect.entity.LabTest;
import in.healthconnect.entity.OrderedTest;
import in.healthconnect.entity.enums.OrderedTestStatus;
import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.repository.LabTestRepository;
import in.healthconnect.repository.OrderedTestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// The lab-test catalogue and the tests ordered against appointments.
@Service
@RequiredArgsConstructor
public class TestOrderService {

    private final LabTestRepository labTestRepository;
    private final OrderedTestRepository orderedTestRepository;

    @Transactional(readOnly = true)
    public List<LabTestResponse> catalog() {
        return labTestRepository.findByActiveTrueOrderByNameAsc().stream().map(LabTestResponse::from).toList();
    }

    // Order each chosen catalogue test for the appointment (name/category snapshotted).
    @Transactional
    public List<OrderedTestResponse> order(Integer appointmentId, List<Integer> labTestIds) {
        for (LabTest test : labTestRepository.findAllById(labTestIds == null ? List.of() : labTestIds)) {
            orderedTestRepository.save(OrderedTest.builder()
                    .appointmentId(appointmentId)
                    .testName(test.getName())
                    .category(test.getCategory())
                    .status(OrderedTestStatus.ORDERED)
                    .build());
        }
        return listOrdered(appointmentId);
    }

    @Transactional(readOnly = true)
    public List<OrderedTestResponse> listOrdered(Integer appointmentId) {
        return orderedTestRepository.findByAppointmentIdOrderByIdAsc(appointmentId).stream()
                .map(OrderedTestResponse::from).toList();
    }

    // Admin links an uploaded report to an ordered test, which marks it available.
    @Transactional
    public void markAvailable(Integer orderedTestId, Integer reportId) {
        OrderedTest test = orderedTestRepository.findById(orderedTestId)
                .orElseThrow(() -> new ResourceNotFoundException("Ordered test not found: " + orderedTestId));
        test.setStatus(OrderedTestStatus.AVAILABLE);
        test.setReportId(reportId);
        orderedTestRepository.save(test);
    }
}
