package in.healthconnect.widgetengine.service;

import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.widgetengine.engine.*;
import in.healthconnect.widgetengine.entity.WidgetFilter;
import in.healthconnect.widgetengine.repository.WidgetFilterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

// The filter catalogue: what a reusable filter is, and how to fetch its choices.
//
// Its `source` query goes through exactly the same gate a widget query does - read-only
// SELECT, single statement, values bound not concatenated. A stored query nobody checks is
// precisely what the guard exists to catch, and "an admin wrote it" is not a check.
@Service
@RequiredArgsConstructor
public class WidgetFilterService {

    private final WidgetFilterRepository filterRepository;
    private final SqlSafetyGuard safetyGuard;
    private final SqlTemplateEngine templateEngine;
    private final WidgetQueryExecutor queryExecutor;

    @Transactional(readOnly = true)
    public List<WidgetFilter> list() {
        return filterRepository.findAll();
    }

    public WidgetFilter save(WidgetFilter filter) {
        safetyGuard.assertSelectOnly(safetyGuard.normalize(filter.getSource()));
        filter.setSource(safetyGuard.normalize(filter.getSource()));
        return filterRepository.save(filter);
    }

    // Run a filter's source query and return its rows: whatever two columns it selected,
    // conventionally an id and a label.
    //
    // Parameters arrive by name from the caller, the same flat bag a widget takes, so a
    // list can narrow itself with :portfolioId or :userExternalId without this service
    // knowing what those mean.
    @Transactional(readOnly = true)
    public ExecutionResult options(String id, Map<String, Object> params, Integer pageSize) {
        WidgetFilter filter = filterRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Filter not found: " + id));

        String sql = safetyGuard.normalize(filter.getSource());
        safetyGuard.assertSelectOnly(sql);

        PreparedQuery prepared = templateEngine.build(QueryBuildRequest.builder()
                .template(sql)
                .namedValues(params)
                .pageNo(1)
                .pageSize(pageSize)
                .build());

        // Technical columns are kept: an options list is nothing BUT ids and labels, and
        // hiding a column called "id" would leave the dropdown with no value to send.
        return queryExecutor.execute(prepared, false);
    }
}
