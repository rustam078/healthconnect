package in.healthconnect.widgetengine.service;

import in.healthconnect.widgetengine.dto.request.ExecuteWidgetRequest;
import in.healthconnect.widgetengine.dto.request.FilterValue;
import in.healthconnect.widgetengine.dto.response.WidgetDataResponse;
import in.healthconnect.widgetengine.engine.*;
import in.healthconnect.widgetengine.entity.Widget;
import in.healthconnect.widgetengine.entity.WidgetExecutionLog;
import in.healthconnect.widgetengine.entity.enums.WidgetModule;
import in.healthconnect.widgetengine.entity.enums.WidgetType;
import in.healthconnect.widgetengine.repository.WidgetExecutionLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

// Tests for WidgetExecutionService - the class that joins everything together.
// Real engine + guard + parser (so the real query build runs), but a MOCK executor
// (so no database) and a MOCK log repository (so we can check the audit record).
class WidgetExecutionServiceTest {

    private final WidgetService widgetService = mock(WidgetService.class);
    private final WidgetQueryExecutor executor = mock(WidgetQueryExecutor.class);
    private final WidgetExecutionLogRepository logRepository = mock(WidgetExecutionLogRepository.class);

    private final SqlSafetyGuard safetyGuard = new SqlSafetyGuard();
    private final SqlTemplateEngine engine = new SqlTemplateEngine(safetyGuard);
    private final FilterConfigParser parser = new FilterConfigParser(JsonMapper.builder().build());

    private final WidgetExecutionService service = new WidgetExecutionService(
            widgetService, parser, engine, executor, logRepository, safetyGuard,
            JsonMapper.builder().build());

    private Widget sampleWidget(boolean enabled) {
        Widget widget = new Widget();
        widget.setId(7);
        widget.setCode("active-patients");
        widget.setModule(WidgetModule.WIDGET);
        widget.setType(WidgetType.TABLE);
        widget.setEnabled(enabled);
        widget.setSqlTemplate(
                "SELECT name AS `Patient` FROM patients WHERE 1=1 " +
                "AND status {{status}} coalesce(:status, status)");
        widget.setFilters(
                "{ \"filters\": [ { \"key\":\"status\", \"operators\":[\"eq\",\"in\"] } ], " +
                "\"sortableColumns\": [] }");
        return widget;
    }

    private ExecuteWidgetRequest requestWithStatus() {
        ExecuteWidgetRequest request = new ExecuteWidgetRequest();
        request.setFilters(Map.of("status", new FilterValue("eq", List.of("ACTIVE"))));
        request.setPageNo(1);
        request.setPageSize(10);
        return request;
    }

    @Test
    void runsWidgetAndReturnsDataAndLogsSuccess() {
        when(widgetService.findByIdOrCode("active-patients")).thenReturn(sampleWidget(true));
        when(executor.execute(any(PreparedQuery.class), anyBoolean()))
                .thenReturn(new ExecutionResult(List.of(Map.of("Patient", "A")), false));

        WidgetDataResponse response = service.execute("active-patients", requestWithStatus(), true);

        assertEquals(1, response.getRowCount());
        assertEquals("A", response.getRows().get(0).get("Patient"));
        assertEquals(1, response.getPageNo());
        assertEquals(10, response.getPageSize());
        assertFalse(response.isHasNext());

        // an audit row was saved, marked success
        ArgumentCaptor<WidgetExecutionLog> logCaptor = ArgumentCaptor.forClass(WidgetExecutionLog.class);
        verify(logRepository).save(logCaptor.capture());
        WidgetExecutionLog log = logCaptor.getValue();
        assertTrue(log.isSuccess());
        assertEquals(1, log.getRowCount());
        assertEquals("active-patients", log.getWidgetCode());
    }

    @Test
    void disabledWidgetIsRejected() {
        when(widgetService.findByIdOrCode("active-patients")).thenReturn(sampleWidget(false));

        assertThrows(IllegalArgumentException.class,
                () -> service.execute("active-patients", requestWithStatus(), true));
    }

    @Test
    void databaseErrorIsLoggedAndHiddenFromClient() {
        when(widgetService.findByIdOrCode("active-patients")).thenReturn(sampleWidget(true));
        when(executor.execute(any(PreparedQuery.class), anyBoolean()))
                .thenThrow(new RuntimeException("DB boom: table patients missing column xyz"));

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> service.execute("active-patients", requestWithStatus(), true));

        // the client sees a generic message, NOT the real database error
        assertFalse(thrown.getMessage().contains("DB boom"));

        // but a failure audit row was saved
        ArgumentCaptor<WidgetExecutionLog> logCaptor = ArgumentCaptor.forClass(WidgetExecutionLog.class);
        verify(logRepository).save(logCaptor.capture());
        assertFalse(logCaptor.getValue().isSuccess());
    }
}
