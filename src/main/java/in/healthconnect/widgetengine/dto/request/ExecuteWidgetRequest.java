package in.healthconnect.widgetengine.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

// The data the client sends to RUN a widget and get its data.
// Everything here is optional - a widget can be run with no filters and default paging.
//
// Example JSON:
//   {
//     "filters": { "status": { "operator": "in", "values": ["ACTIVE"] } },
//     "sortBy": "Patient", "sortOrder": "asc",
//     "pageNo": 1, "pageSize": 20
//   }
@Getter
@Setter
@NoArgsConstructor
public class ExecuteWidgetRequest {

    // filter name -> the operator + value(s) chosen for it
    private Map<String, FilterValue> filters;

    // sorting choice
    private String sortBy;
    private String sortOrder;

    // paging choice (page 1 = first page)
    private Integer pageNo;
    private Integer pageSize;

    // Ask for a total row count alongside the page. Opt-in because it costs a second query
    // over the same rows - a table needs it to draw page numbers, a COUNT card does not.
    private Boolean withTotal;

    // Anything else in the body, kept by name.
    //
    // This is what lets a query take :templateName or :fromDate without anyone declaring
    // it first. A parameter is a parameter; a FILTER is only needed when the screen has to
    // draw a control for it. Values are bound by JDBC, never concatenated, so an unexpected
    // name is at worst ignored - it can never become SQL.
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final Map<String, Object> params = new HashMap<>();

    @JsonAnySetter
    public void putParam(String name, Object value) {
        params.put(name, value);
    }

    public Map<String, Object> getParams() {
        return params;
    }
}
