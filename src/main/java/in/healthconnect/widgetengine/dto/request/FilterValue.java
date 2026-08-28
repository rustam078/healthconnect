package in.healthconnect.widgetengine.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

// One filter the user chose when asking for widget data.
//   operator = a short word like "eq", "in", "like"
//   values   = the value(s) to compare against
// Example JSON:  { "operator": "in", "values": ["ACTIVE", "PENDING"] }
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FilterValue {

    private String operator;
    private List<String> values;
}
