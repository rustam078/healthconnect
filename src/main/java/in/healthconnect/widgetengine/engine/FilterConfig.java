package in.healthconnect.widgetengine.engine;

import java.util.List;

// The widget's filter settings AFTER we read them from JSON.
//   rules           = the filters this widget allows (each with its key, operators, required)
//   sortableColumns = the columns the user is allowed to sort by
// The engine (Step 3) uses these to know what is allowed.
public record FilterConfig(List<FilterRule> rules, List<String> sortableColumns) {
}
