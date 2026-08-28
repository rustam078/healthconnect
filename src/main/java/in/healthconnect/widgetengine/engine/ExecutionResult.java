package in.healthconnect.widgetengine.engine;

import java.util.List;
import java.util.Map;

// The result of running a widget's query.
//   rows    = the data, one Map per row (column name -> value), in column order
//   hasNext = true if there is another page after this one
public record ExecutionResult(List<Map<String, Object>> rows, boolean hasNext) {
}
