package in.healthconnect.widgetengine.service;

import in.healthconnect.widgetengine.dto.request.ExecuteWidgetRequest;
import in.healthconnect.widgetengine.dto.request.FilterValue;
import in.healthconnect.widgetengine.dto.response.WidgetDataResponse;
import in.healthconnect.widgetengine.engine.*;
import in.healthconnect.widgetengine.entity.Widget;
import in.healthconnect.widgetengine.entity.WidgetExecutionLog;
import in.healthconnect.widgetengine.repository.WidgetExecutionLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

// This service joins everything together to actually run a widget:
//   1. load the widget (by id or code)
//   2. read its filter settings
//   3. ask the engine to build the final query (values bound, operators safe)
//   4. run the query
//   5. save a history/audit row (success or failure, how many rows, how long)
//   6. if anything goes wrong, hide the real database error from the client
@Service
public class WidgetExecutionService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final WidgetService widgetService;
    private final FilterConfigParser filterConfigParser;
    private final SqlTemplateEngine templateEngine;
    private final WidgetQueryExecutor queryExecutor;
    private final WidgetExecutionLogRepository logRepository;
    private final SqlSafetyGuard safetyGuard;
    private final ObjectMapper objectMapper;

    public WidgetExecutionService(WidgetService widgetService,
                                  FilterConfigParser filterConfigParser,
                                  SqlTemplateEngine templateEngine,
                                  WidgetQueryExecutor queryExecutor,
                                  WidgetExecutionLogRepository logRepository,
                                  SqlSafetyGuard safetyGuard,
                                  ObjectMapper objectMapper) {
        this.widgetService = widgetService;
        this.filterConfigParser = filterConfigParser;
        this.templateEngine = templateEngine;
        this.queryExecutor = queryExecutor;
        this.logRepository = logRepository;
        this.safetyGuard = safetyGuard;
        this.objectMapper = objectMapper;
    }

    // Run a widget and return its data.
    public WidgetDataResponse execute(String idOrCode, ExecuteWidgetRequest request, boolean hideTechnicalColumns) {
        Widget widget = loadRunnableWidget(idOrCode);

        // Build the final query (safe values + safe operators + sorting + paging).
        PreparedQuery prepared = buildQuery(widget, request);

        int pageNo = request.getPageNo() == null || request.getPageNo() < 1 ? 1 : request.getPageNo();
        long start = System.currentTimeMillis();
        try {
            ExecutionResult result = queryExecutor.execute(prepared, hideTechnicalColumns);
            long durationMs = System.currentTimeMillis() - start;

            // save a "success" audit row
            saveLog(widget, request, result.rows().size(), durationMs, true, null);

            return WidgetDataResponse.of(result, pageNo, prepared.getPageSize());
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - start;
            // log the real problem on the server (full detail) ...
            logger.error("Widget execution failed for '{}': {}", widget.getCode(), e.getMessage(), e);
            // ... save a "failure" audit row (short real message) ...
            saveLog(widget, request, 0, durationMs, false, shorten(e.getMessage()));
            // ... but tell the client only a generic message (no query/table/column leaks).
            throw new RuntimeException("Unable to run this widget. Please try again later.");
        }
    }

    // Build the final query WITHOUT running it (used by the preview endpoint).
    public PreparedQuery buildPreview(String idOrCode, ExecuteWidgetRequest request) {
        Widget widget = loadRunnableWidget(idOrCode);
        return buildQuery(widget, request);
    }

    // ---- helpers ----

    // Load the widget and make sure it is allowed to run.
    private Widget loadRunnableWidget(String idOrCode) {
        Widget widget = widgetService.findByIdOrCode(idOrCode);
        if (widget.getEnabled() == null || !widget.getEnabled()) {
            throw new IllegalArgumentException("Widget '" + widget.getCode() + "' is turned off.");
        }
        return widget;
    }

    // Read the widget's settings and ask the engine to build the query.
    private PreparedQuery buildQuery(Widget widget, ExecuteWidgetRequest request) {
        // extra safety: re-check the stored query is a read-only SELECT
        safetyGuard.assertSelectOnly(widget.getSqlTemplate());

        FilterConfig config = filterConfigParser.parse(widget.getFilters());

        QueryBuildRequest buildRequest = QueryBuildRequest.builder()
                .template(widget.getSqlTemplate())
                .filters(toEngineFilters(request.getFilters()))
                .rules(config.rules())
                .sortableColumns(config.sortableColumns())
                .sortBy(request.getSortBy())
                .sortOrder(request.getSortOrder())
                .pageNo(request.getPageNo())
                .pageSize(request.getPageSize())
                .build();

        return templateEngine.build(buildRequest);
    }

    // Convert the request's filters (DTO) into the engine's filter inputs.
    private Map<String, FilterInput> toEngineFilters(Map<String, FilterValue> requestFilters) {
        Map<String, FilterInput> engineFilters = new HashMap<>();
        if (requestFilters != null) {
            for (Map.Entry<String, FilterValue> entry : requestFilters.entrySet()) {
                FilterValue value = entry.getValue();
                engineFilters.put(entry.getKey(), new FilterInput(value.getOperator(), value.getValues()));
            }
        }
        return engineFilters;
    }

    // Save one audit row for this execution.
    private void saveLog(Widget widget, ExecuteWidgetRequest request, int rowCount,
                         long durationMs, boolean success, String errorMessage) {
        WidgetExecutionLog log = WidgetExecutionLog.builder()
                .widgetId(widget.getId())
                .widgetCode(widget.getCode())
                .module(widget.getModule() == null ? null : widget.getModule().name())
                .paramsJson(toJsonText(request.getFilters()))
                .rowCount(rowCount)
                .durationMs(durationMs)
                .success(success)
                .errorMessage(errorMessage)
                .executedAt(Instant.now())
                .build();
        logRepository.save(log);
    }

    private String toJsonText(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null; // never let logging break the request
        }
    }

    // Keep the stored error message short (the column is limited).
    private String shorten(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 480 ? message.substring(0, 480) : message;
    }
}
