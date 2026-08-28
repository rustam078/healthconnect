package in.healthconnect.widgetengine.service;

import in.healthconnect.widgetengine.engine.FilterConfig;
import in.healthconnect.widgetengine.engine.FilterRule;
import in.healthconnect.widgetengine.entity.enums.FilterOperator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Tests for FilterConfigParser.
// It reads the widget's "filters" JSON text and turns it into a FilterConfig
// (the list of allowed filters + the list of sortable columns).
class FilterConfigParserTest {

    // A plain Jackson mapper for the test (the real app injects Spring's mapper).
    private final FilterConfigParser parser = new FilterConfigParser(JsonMapper.builder().build());

    @Test
    void emptyOrNullJsonGivesEmptyConfig() {
        FilterConfig config = parser.parse(null);
        assertTrue(config.rules().isEmpty());
        assertTrue(config.sortableColumns().isEmpty());
    }

    @Test
    void parsesFiltersAndSortableColumns() {
        String json = "{ \"filters\": [ " +
                "{ \"key\": \"status\", \"operators\": [\"eq\", \"in\"], \"required\": true } " +
                "], \"sortableColumns\": [\"Patient\", \"Status\"] }";

        FilterConfig config = parser.parse(json);

        assertEquals(1, config.rules().size());
        FilterRule rule = config.rules().get(0);
        assertEquals("status", rule.key());
        assertTrue(rule.allowedOperators().contains(FilterOperator.EQ));
        assertTrue(rule.allowedOperators().contains(FilterOperator.IN));
        assertTrue(rule.required());
        assertEquals(List.of("Patient", "Status"), config.sortableColumns());
    }

    @Test
    void requiredDefaultsToFalseWhenNotGiven() {
        String json = "{ \"filters\": [ { \"key\": \"status\", \"operators\": [\"eq\"] } ] }";

        FilterConfig config = parser.parse(json);

        assertFalse(config.rules().get(0).required());
    }

    @Test
    void unknownOperatorInConfigIsRejected() {
        String json = "{ \"filters\": [ { \"key\": \"status\", \"operators\": [\"bogus\"] } ] }";

        assertThrows(IllegalArgumentException.class, () -> parser.parse(json));
    }

    @Test
    void invalidJsonIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("{ not valid json"));
    }
}
