package in.healthconnect.widgetengine.engine;

import in.healthconnect.widgetengine.entity.enums.FilterOperator;

import java.util.Set;

// What the WIDGET allows for one filter (this comes from the widget's saved settings).
//   key               = the filter name, e.g. "status"
//   allowedOperators  = which operators are allowed for this filter (e.g. eq, in)
//   required          = if true, the user MUST send this filter
public record FilterRule(String key, Set<FilterOperator> allowedOperators, boolean required) {
}
