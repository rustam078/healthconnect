package in.healthconnect.widgetengine.service;

import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.widgetengine.dto.request.CreateWidgetRequest;
import in.healthconnect.widgetengine.dto.response.WidgetResponse;
import in.healthconnect.widgetengine.engine.SqlSafetyGuard;
import in.healthconnect.widgetengine.entity.Widget;
import in.healthconnect.widgetengine.entity.enums.WidgetStatus;
import in.healthconnect.widgetengine.entity.enums.WidgetModule;
import in.healthconnect.widgetengine.entity.enums.WidgetType;
import in.healthconnect.widgetengine.repository.WidgetRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Tests for WidgetService (the create/read/update/delete logic).
// We use a REAL SqlSafetyGuard, and a FAKE (mock) repository so we don't need a database.
class WidgetServiceTest {

    private final WidgetRepository repository = mock(WidgetRepository.class);
    private final SqlSafetyGuard safetyGuard = new SqlSafetyGuard();
    private final WidgetService service = new WidgetService(repository, safetyGuard);

    private CreateWidgetRequest validCreateRequest() {
        CreateWidgetRequest request = new CreateWidgetRequest();
        request.setCode("active-patients");
        request.setName("Active Patients");
        request.setModule(WidgetModule.WIDGET);
        request.setType(WidgetType.COUNT);
        request.setSqlTemplate("SELECT count(*) AS total FROM patients");
        return request;
    }

    @Test
    void createRejectsDuplicateCode() {
        when(repository.countByCodeIncludingDeleted("active-patients")).thenReturn(1L);

        assertThrows(IllegalArgumentException.class, () -> service.create(validCreateRequest()));
    }

    @Test
    void createRejectsNonSelectTemplate() {
        when(repository.countByCodeIncludingDeleted(any())).thenReturn(0L);
        CreateWidgetRequest request = validCreateRequest();
        request.setSqlTemplate("DELETE FROM patients"); // not a SELECT -> must be rejected

        assertThrows(IllegalArgumentException.class, () -> service.create(request));
    }

    @Test
    void createSavesValidWidget() {
        when(repository.countByCodeIncludingDeleted(any())).thenReturn(0L);
        // the fake save just returns the widget it was given
        when(repository.save(any(Widget.class))).thenAnswer(call -> call.getArgument(0));

        WidgetResponse response = service.create(validCreateRequest());

        assertEquals("active-patients", response.getCode());
        assertEquals("Active Patients", response.getName());
        assertEquals(WidgetModule.WIDGET, response.getModule());
        assertTrue(response.getEnabled()); // defaults to on
        verify(repository).save(any(Widget.class));
    }

    @Test
    void getByCodeFindsWidget() {
        Widget widget = validWidget();
        when(repository.findByCode("active-patients")).thenReturn(Optional.of(widget));

        WidgetResponse response = service.getByIdOrCode("active-patients");

        assertEquals("active-patients", response.getCode());
    }

    @Test
    void getByNumericIdFindsWidget() {
        Widget widget = validWidget();
        when(repository.findById(5)).thenReturn(Optional.of(widget));

        WidgetResponse response = service.getByIdOrCode("5");

        assertEquals("active-patients", response.getCode());
    }

    @Test
    void getByIdOrCodeThrowsWhenMissing() {
        when(repository.findByCode("nope")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getByIdOrCode("nope"));
    }

    @Test
    void deleteRemovesWidget() {
        Widget widget = validWidget();
        when(repository.findById(5)).thenReturn(Optional.of(widget));

        service.delete(5);

        verify(repository).delete(widget);
    }

    private Widget validWidget() {
        Widget widget = new Widget();
        widget.setCode("active-patients");
        widget.setName("Active Patients");
        widget.setModule(WidgetModule.WIDGET);
        widget.setType(WidgetType.COUNT);
        widget.setSqlTemplate("SELECT count(*) AS total FROM patients");
        widget.setEnabled(true);
        return widget;
    }

    @Test
    void deletingAnUnapprovedDraftReallyRemovesIt() {
        // A DRAFT is a throwaway from asking the AI - often one whose query does not even
        // run. Soft-deleting it would keep its code in the uk_widget_code unique index, so
        // re-asking the same question would come back as "-2". Hard delete frees the code.
        Widget draft = validWidget();
        draft.setStatus(WidgetStatus.DRAFT);
        when(repository.findById(5)).thenReturn(Optional.of(draft));

        service.delete(5);

        verify(repository).hardDeleteById(5);
        verify(repository, never()).delete(any(Widget.class));
    }

    @Test
    void deletingAnApprovedWidgetStillSoftDeletes() {
        // Boards may point at it and the execution log refers to it, so it stays recoverable.
        Widget approved = validWidget();
        approved.setStatus(WidgetStatus.APPROVED);
        when(repository.findById(6)).thenReturn(Optional.of(approved));

        service.delete(6);

        verify(repository).delete(approved);
        verify(repository, never()).hardDeleteById(any());
    }

    @Test
    void anOlderWidgetWithNoStatusIsTreatedAsApproved() {
        // Rows created before WidgetStatus existed have a null status. Hard-deleting those
        // would be destroying data someone may still be using.
        Widget legacy = validWidget();
        legacy.setStatus(null);
        when(repository.findById(7)).thenReturn(Optional.of(legacy));

        service.delete(7);

        verify(repository).delete(legacy);
        verify(repository, never()).hardDeleteById(any());
    }
}
