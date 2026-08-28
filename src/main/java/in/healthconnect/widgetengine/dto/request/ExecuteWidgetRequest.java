package in.healthconnect.widgetengine.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
@AllArgsConstructor
public class ExecuteWidgetRequest {

    // filter name -> the operator + value(s) chosen for it
    private Map<String, FilterValue> filters;

    // sorting choice
    private String sortBy;
    private String sortOrder;

    // paging choice (page 1 = first page)
    private Integer pageNo;
    private Integer pageSize;
}
