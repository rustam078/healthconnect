package in.healthconnect.widgetengine.engine;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

// Everything the engine needs to build the final query, in one object.
// We use @Builder so tests and services can set only the fields they care about.
@Getter
@Builder
public class QueryBuildRequest {

    // the saved query with :name and {{name}} blanks
    private String template;

    // what the user sent: filter name -> its operator + value(s)
    @Builder.Default
    private Map<String, FilterInput> filters = Map.of();

    // what the widget allows (its filter settings)
    @Builder.Default
    private List<FilterRule> rules = List.of();

    // which columns the user is allowed to sort by
    @Builder.Default
    private List<String> sortableColumns = List.of();

    // sorting choice from the user
    private String sortBy;
    private String sortOrder;

    // paging choice from the user (1 = first page)
    private Integer pageNo;
    private Integer pageSize;
}
