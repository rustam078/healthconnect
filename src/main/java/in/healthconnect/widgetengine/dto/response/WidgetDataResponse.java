package in.healthconnect.widgetengine.dto.response;

import in.healthconnect.widgetengine.engine.ExecutionResult;
import lombok.*;

import java.util.List;
import java.util.Map;

// The data we send back after running a widget.
//   rows     = the actual data rows (each row is column name -> value)
//   rowCount = how many rows are in this page
//   pageNo   = which page this is (1 = first)
//   pageSize = how many rows per page we used
//   hasNext  = is there another page after this one?
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WidgetDataResponse {

    private List<Map<String, Object>> rows;
    private int rowCount;
    private int pageNo;
    private int pageSize;
    private boolean hasNext;

    // Build this response from the engine's result plus the paging info we used.
    public static WidgetDataResponse of(ExecutionResult result, int pageNo, int pageSize) {
        return WidgetDataResponse.builder()
                .rows(result.rows())
                .rowCount(result.rows().size())
                .pageNo(pageNo)
                .pageSize(pageSize)
                .hasNext(result.hasNext())
                .build();
    }
}
