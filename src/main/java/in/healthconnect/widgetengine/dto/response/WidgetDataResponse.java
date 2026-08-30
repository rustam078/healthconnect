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

    // How many rows match in total, ignoring paging. Null unless the caller asked for it:
    // counting means a second query over the same rows, which a COUNT card or a chart has
    // no use for.
    private Long totalElements;

    // Build this response from the engine's result plus the paging info we used.
    public static WidgetDataResponse of(ExecutionResult result, int pageNo, int pageSize) {
        return of(result, pageNo, pageSize, null);
    }

    public static WidgetDataResponse of(ExecutionResult result, int pageNo, int pageSize, Long totalElements) {
        return WidgetDataResponse.builder()
                .totalElements(totalElements)
                .rows(result.rows())
                .rowCount(result.rows().size())
                .pageNo(pageNo)
                .pageSize(pageSize)
                .hasNext(result.hasNext())
                .build();
    }
}
