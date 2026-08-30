package in.healthconnect.widgetengine.engine;

import in.healthconnect.widgetengine.entity.enums.FilterOperator;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// This class builds the FINAL query from a saved template + the filters the user sent.
//
// The template has two kinds of blanks:
//   :name     -> a VALUE. We keep it as :name and bind the real value separately (safe).
//   {{name}}  -> an OPERATOR (=, IN, LIKE ...). We fill it from our safe list.
//
// It then adds sorting (only on approved columns) and paging (LIMIT/OFFSET).
// It does NOT run the query - it only prepares it. Running happens in the next class.
@Component
public class SqlTemplateEngine {

    // If the user does not choose a page size, use this.
    private static final int DEFAULT_PAGE_SIZE = 50;
    // Never allow a page bigger than this (protects the database).
    private static final int MAX_PAGE_SIZE = 200;

    private final SqlSafetyGuard safetyGuard;

    public SqlTemplateEngine(SqlSafetyGuard safetyGuard) {
        this.safetyGuard = safetyGuard;
    }

    public PreparedQuery build(QueryBuildRequest request) {
        String sql = request.getTemplate();
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("Query template is empty.");
        }

        // The "bag" of values that will be bound safely at run time.
        MapSqlParameterSource params = new MapSqlParameterSource();

        Map<String, FilterInput> filters = request.getFilters();
        List<FilterRule> rules = request.getRules();

        // Put the widget's rules into a quick lookup by filter name.
        Map<String, FilterRule> ruleByKey = new HashMap<>();
        for (FilterRule rule : rules) {
            ruleByKey.put(rule.key(), rule);
        }

        // ---- 1. Handle each filter the user actually sent ----
        for (Map.Entry<String, FilterInput> entry : filters.entrySet()) {
            String key = entry.getKey();
            FilterInput input = entry.getValue();

            // The widget must allow this filter.
            FilterRule rule = ruleByKey.get(key);
            if (rule == null) {
                throw new IllegalArgumentException("Unknown filter: " + key);
            }

            // The operator word must be known AND allowed for this filter.
            FilterOperator operator = safetyGuard.requireOperator(input.operator());
            if (!rule.allowedOperators().contains(operator)) {
                throw new IllegalArgumentException(
                        "Operator '" + operator.getKey() + "' is not allowed for filter '" + key + "'.");
            }

            // Fill the operator blank with our safe symbol, and bind the value(s).
            sql = sql.replace("{{" + key + "}}", operator.getSymbol());
            params.addValue(key, buildValue(operator, input.values()));
        }

        // ---- 2. Every required filter must have been sent ----
        for (FilterRule rule : rules) {
            if (rule.required() && !filters.containsKey(rule.key())) {
                throw new IllegalArgumentException("Filter '" + rule.key() + "' is required.");
            }
        }

        // ---- 3. Make leftover blanks harmless (filters the user did not send) ----
        // Any leftover {{...}} becomes "=" ...
        sql = sql.replaceAll("\\{\\{\\s*\\w+\\s*\\}\\}", "=");
        // Values sent by name in the request body, for blanks that no filter declared.
        // A query can then take :fromDate or :templateName without a filter existing for
        // it - a filter is only needed when a screen has to draw a control.
        Map<String, Object> named = request.getNamedValues();
        if (named != null) {
            for (Map.Entry<String, Object> entry : named.entrySet()) {
                // A declared filter wins: it went through the operator checks above, and
                // its value was shaped for the operator it is being used with.
                if (!params.hasValue(entry.getKey())) {
                    params.addValue(entry.getKey(), entry.getValue());
                }
            }
        }

        // ... and any leftover :name that we did not bind yet becomes null.
        // (Templates use coalesce(:name, column) so a null means "do not filter".)
        for (String variable : extractNamedVariables(sql)) {
            if (!params.hasValue(variable)) {
                params.addValue(variable, null);
            }
        }

        // Does the stored query already end with its own LIMIT (e.g. "top 5 doctors")?
        // If so, anything we append lands AFTER that LIMIT and MySQL rejects it. Work this
        // out BEFORE the sort, because "... LIMIT 5 order by x" is just as invalid as
        // "... LIMIT 5 limit ? offset ?".
        boolean ownLimit = hasOwnLimit(sql);

        // ---- 4. Sorting (only on approved columns) ----
        String sortBy = request.getSortBy();
        List<String> sortable = request.getSortableColumns();
        if (!ownLimit
                && sortBy != null && !sortBy.isBlank()
                && sortable != null && sortable.contains(sortBy)
                && sortBy.matches("[A-Za-z0-9_ ]+")) {   // extra safety: only simple names
            String direction = "desc".equalsIgnoreCase(request.getSortOrder()) ? "desc" : "asc";
            // Back-ticks are how MySQL quotes a column name.
            sql += " order by `" + sortBy + "` " + direction;
        }

        // ---- 5. Pagination (LIMIT / OFFSET) ----
        int pageSize = request.getPageSize() == null ? DEFAULT_PAGE_SIZE : request.getPageSize();
        if (pageSize < 1) {
            pageSize = DEFAULT_PAGE_SIZE;
        }
        if (pageSize > MAX_PAGE_SIZE) {
            pageSize = MAX_PAGE_SIZE;   // never allow a huge page
        }

        int pageNo = request.getPageNo() == null ? 1 : request.getPageNo();
        if (pageNo < 1) {
            pageNo = 1;                 // pages start at 1
        }
        int offset = (pageNo - 1) * pageSize;

        // Remembered before paging is appended - this is what a COUNT wraps.
        String unpaged = sql;

        if (!ownLimit) {
            sql += " limit :__pageSize offset :__offset";
            // Ask for ONE extra row. If we get it back, we know there is a next page.
            params.addValue("__pageSize", pageSize + 1);
            params.addValue("__offset", offset);
        }
        // When the query limits itself we add nothing. The executor still trims to
        // pageSize, so the page cap keeps protecting us; a "top 5" simply returns its 5.

        // Pass the real pageSize along so the executor knows where to cut the extra row.
        return new PreparedQuery(sql, params, pageSize, unpaged);
    }


    // Does the stored query end with its own LIMIT?
    //
    // "list the top 5 doctors" legitimately needs LIMIT 5, and AI-written queries use it
    // freely. When one does, we must NOT append our own "order by ..." or
    // "limit ? offset ?" - both would land AFTER the existing LIMIT and MySQL rejects that
    // ("... LIMIT 5 limit ? offset ?" is a syntax error).
    //
    // Anchored to the END of the statement, so a LIMIT inside a subquery in the middle of
    // the query does not count. Quoted text is blanked first so a literal like 'limit 5'
    // in a WHERE clause cannot trigger it.
    static boolean hasOwnLimit(String sql) {
        if (sql == null) {
            return false;
        }
        String withoutStrings = sql.replaceAll("'([^']|'')*'", "''");
        return withoutStrings.matches("(?is).*\\blimit\\s+\\d+\\s*(,\\s*\\d+\\s*)?(offset\\s+\\d+\\s*)?;?\\s*");
    }

    // Decide what value to bind, based on the operator:
    //   IN / NOT IN  -> the whole list of values
    //   LIKE         -> the first value wrapped in %...% for "contains" search
    //   others       -> just the first value
    private Object buildValue(FilterOperator operator, List<String> values) {
        if (operator.isList()) {
            return values == null ? List.of() : values;
        }
        String first = (values == null || values.isEmpty()) ? null : values.get(0);
        if (operator.isLike()) {
            return first == null ? null : "%" + first + "%";
        }
        return first;
    }

    // Find all the :name placeholders in the query.
    // Before searching, we hide text inside quotes and ignore "::" so we don't
    // pick up colons that are not really placeholders.
    private List<String> extractNamedVariables(String sql) {
        List<String> variables = new ArrayList<>();
        String scrubbed = sql.replace("::", " ");
        scrubbed = scrubbed.replaceAll("'[^']*'", "''");
        Matcher matcher = Pattern.compile(":(\\w+)").matcher(scrubbed);
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
        return variables;
    }
}
