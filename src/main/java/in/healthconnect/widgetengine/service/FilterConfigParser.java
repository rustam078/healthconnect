package in.healthconnect.widgetengine.service;

import in.healthconnect.widgetengine.engine.FilterConfig;
import in.healthconnect.widgetengine.engine.FilterRule;
import in.healthconnect.widgetengine.entity.enums.FilterOperator;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// Reads the widget's "filters" JSON text and turns it into a FilterConfig
// (the allowed filters + the sortable columns) that the engine understands.
//
// Example input JSON:
//   { "filters": [ { "key":"status", "operators":["eq","in"], "required":true } ],
//     "sortableColumns": ["Patient","Status"] }
@Component
public class FilterConfigParser {

    private final ObjectMapper objectMapper;

    public FilterConfigParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public FilterConfig parse(String filtersJson) {
        // No settings? Return empty lists (the widget simply has no filters/sorting).
        if (filtersJson == null || filtersJson.isBlank()) {
            return new FilterConfig(List.of(), List.of());
        }

        // Let Jackson read the JSON into simple holder objects (see bottom of file).
        RawConfig raw;
        try {
            raw = objectMapper.readValue(filtersJson, RawConfig.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid filters JSON: " + e.getMessage());
        }

        // Turn each raw filter into a FilterRule the engine can use.
        List<FilterRule> rules = new ArrayList<>();
        if (raw.filters != null) {
            for (RawFilter rawFilter : raw.filters) {
                if (rawFilter.key == null || rawFilter.key.isBlank()) {
                    continue; // skip a filter with no name
                }
                Set<FilterOperator> operators = new LinkedHashSet<>();
                if (rawFilter.operators != null) {
                    for (String operatorKey : rawFilter.operators) {
                        // reuse the same safe lookup - unknown operator = error
                        FilterOperator operator = FilterOperator.fromKey(operatorKey)
                                .orElseThrow(() -> new IllegalArgumentException(
                                        "Unknown operator in filter settings: " + operatorKey));
                        operators.add(operator);
                    }
                }
                rules.add(new FilterRule(rawFilter.key, operators, rawFilter.required));
            }
        }

        List<String> sortableColumns =
                raw.sortableColumns == null ? List.of() : raw.sortableColumns;

        return new FilterConfig(rules, sortableColumns);
    }

    // --- Simple holder classes that match the shape of the JSON ---
    // Jackson fills these in from the JSON. Public fields keep them short.

    static class RawConfig {
        public List<RawFilter> filters;
        public List<String> sortableColumns;
    }

    static class RawFilter {
        public String key;
        public List<String> operators;
        public boolean required; // defaults to false if the JSON does not include it
    }
}
