package in.healthconnect.widgetengine.engine;

import java.util.List;

// What the USER sends for one filter.
//   operator = a short word like "eq", "in", "like"
//   values   = the value(s) to compare against (a list, even if there is only one)
// This is a "record": a tiny class that just holds these two things.
public record FilterInput(String operator, List<String> values) {
}
