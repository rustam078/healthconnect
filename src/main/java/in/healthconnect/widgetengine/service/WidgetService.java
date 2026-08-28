package in.healthconnect.widgetengine.service;

import in.healthconnect.widgetengine.dto.request.CreateWidgetRequest;
import in.healthconnect.widgetengine.dto.request.UpdateWidgetRequest;
import in.healthconnect.widgetengine.dto.response.WidgetResponse;
import in.healthconnect.widgetengine.dto.response.WidgetSummaryResponse;
import in.healthconnect.widgetengine.engine.SqlSafetyGuard;
import in.healthconnect.widgetengine.entity.Widget;
import in.healthconnect.widgetengine.entity.enums.WidgetModule;
import in.healthconnect.widgetengine.entity.enums.WidgetStatus;
import in.healthconnect.widgetengine.repository.WidgetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import in.healthconnect.exception.ResourceNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

// The create / read / update / delete logic for widgets.
// Before saving, it checks two important rules:
//   1. the "code" must be unique;
//   2. the query must be a safe read-only SELECT (checked by SqlSafetyGuard).
@Service
@RequiredArgsConstructor
public class WidgetService {

    private final WidgetRepository widgetRepository;
    private final SqlSafetyGuard safetyGuard;

    // --- CREATE ---
    public WidgetResponse create(CreateWidgetRequest request) {
        // rule 1: no two widgets can share a code.
        // countByCodeIncludingDeleted, not existsByCode: Widget is soft-deleted, so
        // existsByCode cannot see a deleted row - but the uk_widget_code unique index
        // still holds its code, and the insert would fail with a duplicate-key error.
        if (widgetRepository.countByCodeIncludingDeleted(request.getCode()) > 0) {
            throw new IllegalArgumentException(
                    "The code '" + request.getCode() + "' is already taken"
                            + " (possibly by a widget that was deleted). Choose another.");
        }
        // rule 2: the query must be a safe SELECT
        safetyGuard.assertSelectOnly(request.getSqlTemplate());

        Widget widget = new Widget();
        widget.setCode(request.getCode());
        widget.setName(request.getName());
        widget.setDescription(request.getDescription());
        widget.setModule(request.getModule());
        widget.setType(request.getType());
        widget.setSqlTemplate(request.getSqlTemplate());
        widget.setFilters(toJsonText(request.getFilters()));
        // if the client did not say, turn the widget on
        widget.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        // a widget made by hand is ready to use straight away
        widget.setStatus(WidgetStatus.APPROVED);

        return WidgetResponse.from(widgetRepository.save(widget));
    }

    // Mark a DRAFT widget (usually AI-generated) as APPROVED - ready for normal use.
    public WidgetResponse approve(Integer id) {
        Widget widget = widgetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Widget not found: " + id));
        widget.setStatus(WidgetStatus.APPROVED);
        return WidgetResponse.from(widgetRepository.save(widget));
    }

    // --- UPDATE ---
    public WidgetResponse update(Integer id, UpdateWidgetRequest request) {
        Widget widget = widgetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Widget not found:" + id));

        safetyGuard.assertSelectOnly(request.getSqlTemplate());

        widget.setName(request.getName());
        widget.setDescription(request.getDescription());
        widget.setModule(request.getModule());
        widget.setType(request.getType());
        widget.setSqlTemplate(request.getSqlTemplate());
        widget.setFilters(toJsonText(request.getFilters()));
        if (request.getEnabled() != null) {
            widget.setEnabled(request.getEnabled());
        }

        return WidgetResponse.from(widgetRepository.save(widget));
    }

    // --- READ one ---
    @Transactional(readOnly = true)
    public WidgetResponse getByIdOrCode(String idOrCode) {
        return WidgetResponse.from(findByIdOrCode(idOrCode));
    }

    // --- READ many (a page) ---
    @Transactional(readOnly = true)
    public Page<WidgetSummaryResponse> list(WidgetModule module, Pageable pageable) {
        Page<Widget> page = (module == null)
                ? widgetRepository.findAll(pageable)
                : widgetRepository.findByModule(module, pageable);
        // turn each Widget into a short summary
        return page.map(WidgetSummaryResponse::from);
    }

    // Deleting a widget means one of two different things.
    //
    // A DRAFT was never approved: it is a throwaway from asking the AI a question, and
    // possibly one whose query does not even run. Nothing references it and nothing wants
    // it back, so we REALLY delete it. That also frees its code - a soft-deleted row keeps
    // its code in the uk_widget_code unique index, which is what made re-asking the same
    // question produce "-2" and "-3" suffixes.
    //
    // Anything else (APPROVED, or an older row with no status, which we treat as approved)
    // is soft-deleted as before: boards may point at it and the execution log refers to it.
    @Transactional
    public void delete(Integer id) {
        Widget widget = widgetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Widget not found:" + id));

        if (widget.getStatus() == WidgetStatus.DRAFT) {
            widgetRepository.hardDeleteById(id);
            return;
        }
        // thanks to @SQLDelete on the entity, this marks it deleted instead of removing it
        widgetRepository.delete(widget);
    }

    // Find a widget by its number (id) OR its code. Used here and by the execution service.
    // If the text is all digits we treat it as an id, otherwise as a code.
    public Widget findByIdOrCode(String idOrCode) {
        try {
            Integer id = Integer.valueOf(idOrCode);
            return widgetRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Widget not found:" + idOrCode));
        } catch (NumberFormatException notANumber) {
            return widgetRepository.findByCode(idOrCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Widget not found:" + idOrCode));
        }
    }

    // Turn the incoming JSON filters into plain text to store in the database.
    private String toJsonText(JsonNode filters) {
        return filters == null ? null : filters.toString();
    }
}
