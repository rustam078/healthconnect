package in.healthconnect.widgetengine.engine;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

// The RESULT of the engine: the final query text, the bound values, and the page size.
//   sql      = the final query, still using :name placeholders (safe)
//   params   = the values for those placeholders (Spring fills them in safely at run time)
//   pageSize = the real page size we are using (after applying the default and the maximum).
//              The query actually asks for pageSize + 1 rows, so the next class can tell
//              if there is a next page. We keep the real pageSize here so it knows where to cut.
@Getter
@RequiredArgsConstructor
public class PreparedQuery {

    private final String sql;
    private final MapSqlParameterSource params;
    private final int pageSize;
}
