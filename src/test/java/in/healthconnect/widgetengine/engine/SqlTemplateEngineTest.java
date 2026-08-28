package in.healthconnect.widgetengine.engine;

import in.healthconnect.widgetengine.entity.enums.FilterOperator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// Tests for SqlTemplateEngine.
// The engine takes a stored query template + the filters the user sent,
// and builds the FINAL query. Rules we prove here:
//   - the user's VALUES are bound (kept as :name), never pasted into the text
//   - the OPERATOR (=, IN, LIKE ...) is filled from our safe list
//   - filters the user did NOT send are made harmless
//   - a "required" filter must be sent, or we stop
//   - sorting is only allowed on approved columns
//   - pagination adds LIMIT/OFFSET, with a safe maximum page size
class SqlTemplateEngineTest {

    private final SqlTemplateEngine engine = new SqlTemplateEngine(new SqlSafetyGuard());

    // A small sample template used by many tests.
    // :status / :doctorIds  = values we will fill in safely
    // {{status}} / {{doctorIds}} = operators we will fill in from the safe list
    private static final String TEMPLATE =
            "SELECT p.full_name AS `Patient`, p.status AS `Status` " +
            "FROM patients p WHERE 1=1 " +
            "AND p.status {{status}} coalesce(:status, p.status) " +
            "AND p.doctor_id {{doctorIds}} (:doctorIds)";

    // rules = what the widget ALLOWS (its filter metadata)
    private static final List<FilterRule> RULES = List.of(
            new FilterRule("status", Set.of(FilterOperator.EQ, FilterOperator.IN), false),
            new FilterRule("doctorIds", Set.of(FilterOperator.IN), false)
    );

    // helper to read a bound value
    private Object value(PreparedQuery pq, String name) {
        return pq.getParams().getValue(name);
    }

    private int intValue(PreparedQuery pq, String name) {
        return ((Number) pq.getParams().getValue(name)).intValue();
    }

    // ---------- value binding + operator swap ----------

    @Test
    void equalsFilterBindsValueAndKeepsPlaceholder() {
        PreparedQuery pq = engine.build(QueryBuildRequest.builder()
                .template(TEMPLATE)
                .rules(RULES)
                .filters(Map.of("status", new FilterInput("eq", List.of("ACTIVE"))))
                .build());

        // operator "=" was filled in
        assertTrue(pq.getSql().contains("p.status = coalesce(:status, p.status)"));
        // the value was BOUND (placeholder :status is still in the text)
        assertTrue(pq.getSql().contains(":status"));
        // and the bound value is exactly what the user sent
        assertEquals("ACTIVE", value(pq, "status"));
    }

    @Test
    void likeFilterWrapsValueWithPercentSigns() {
        String template = "SELECT * FROM patients p WHERE p.full_name {{name}} :name";
        List<FilterRule> rules = List.of(
                new FilterRule("name", Set.of(FilterOperator.LIKE), false));

        PreparedQuery pq = engine.build(QueryBuildRequest.builder()
                .template(template)
                .rules(rules)
                .filters(Map.of("name", new FilterInput("like", List.of("john"))))
                .build());

        assertTrue(pq.getSql().contains("p.full_name LIKE :name"));
        assertEquals("%john%", value(pq, "name"));
    }

    @Test
    void inFilterBindsAListOfValues() {
        PreparedQuery pq = engine.build(QueryBuildRequest.builder()
                .template(TEMPLATE)
                .rules(RULES)
                .filters(Map.of("doctorIds", new FilterInput("in", List.of("1", "2", "3"))))
                .build());

        assertTrue(pq.getSql().contains("p.doctor_id IN (:doctorIds)"));
        assertEquals(List.of("1", "2", "3"), value(pq, "doctorIds"));
    }

    // ---------- optional filters (not sent by the user) ----------

    @Test
    void missingFilterIsMadeHarmless() {
        // user sends nothing. Both placeholders must be neutralised:
        // operator becomes "=" and the value becomes null (coalesce makes it always true).
        PreparedQuery pq = engine.build(QueryBuildRequest.builder()
                .template(TEMPLATE)
                .rules(RULES)
                .filters(Map.of())
                .build());

        assertTrue(pq.getSql().contains("p.status = coalesce(:status, p.status)"));
        assertNull(value(pq, "status"));
        assertNull(value(pq, "doctorIds"));
    }

    // ---------- required filters ----------

    @Test
    void requiredFilterMustBeProvided() {
        List<FilterRule> rules = List.of(
                new FilterRule("status", Set.of(FilterOperator.EQ), true)); // required = true

        assertThrows(IllegalArgumentException.class, () -> engine.build(QueryBuildRequest.builder()
                .template("SELECT * FROM patients WHERE status {{status}} :status")
                .rules(rules)
                .filters(Map.of()) // not sent -> must fail
                .build()));
    }

    // ---------- operator rules ----------

    @Test
    void operatorNotAllowedForFilterIsRejected() {
        // status only allows eq/in, but the user tries "like"
        assertThrows(IllegalArgumentException.class, () -> engine.build(QueryBuildRequest.builder()
                .template(TEMPLATE)
                .rules(RULES)
                .filters(Map.of("status", new FilterInput("like", List.of("x"))))
                .build()));
    }

    @Test
    void unknownOperatorIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> engine.build(QueryBuildRequest.builder()
                .template(TEMPLATE)
                .rules(RULES)
                .filters(Map.of("status", new FilterInput("bogus", List.of("x"))))
                .build()));
    }

    @Test
    void filterNotDeclaredByWidgetIsRejected() {
        // "secret" is not in the widget's rules -> reject it
        assertThrows(IllegalArgumentException.class, () -> engine.build(QueryBuildRequest.builder()
                .template(TEMPLATE)
                .rules(RULES)
                .filters(Map.of("secret", new FilterInput("eq", List.of("x"))))
                .build()));
    }

    // ---------- sorting ----------

    @Test
    void sortIsAppliedWhenColumnIsAllowed() {
        PreparedQuery pq = engine.build(QueryBuildRequest.builder()
                .template(TEMPLATE)
                .rules(RULES)
                .sortableColumns(List.of("Patient", "Status"))
                .sortBy("Patient")
                .sortOrder("asc")
                .build());

        assertTrue(pq.getSql().contains("order by `Patient` asc"));
    }

    @Test
    void sortDescendingIsApplied() {
        PreparedQuery pq = engine.build(QueryBuildRequest.builder()
                .template(TEMPLATE)
                .rules(RULES)
                .sortableColumns(List.of("Patient"))
                .sortBy("Patient")
                .sortOrder("desc")
                .build());

        assertTrue(pq.getSql().contains("order by `Patient` desc"));
    }

    @Test
    void sortIsIgnoredWhenColumnIsNotAllowed() {
        PreparedQuery pq = engine.build(QueryBuildRequest.builder()
                .template(TEMPLATE)
                .rules(RULES)
                .sortableColumns(List.of("Patient"))
                .sortBy("password") // not allowed -> ignored, no order by
                .sortOrder("asc")
                .build());

        assertFalse(pq.getSql().toLowerCase().contains("order by"));
    }

    // ---------- pagination ----------

    @Test
    void paginationAddsLimitAndOffset() {
        PreparedQuery pq = engine.build(QueryBuildRequest.builder()
                .template(TEMPLATE)
                .rules(RULES)
                .pageNo(1)
                .pageSize(20)
                .build());

        assertTrue(pq.getSql().contains("limit :__pageSize offset :__offset"));
        // we fetch one EXTRA row (21) so we can tell if there is a next page
        assertEquals(21, intValue(pq, "__pageSize"));
        assertEquals(0, intValue(pq, "__offset"));
    }

    @Test
    void pageOffsetIsCalculatedFromPageNumber() {
        PreparedQuery pq = engine.build(QueryBuildRequest.builder()
                .template(TEMPLATE)
                .rules(RULES)
                .pageNo(3)
                .pageSize(10)
                .build());

        assertEquals(11, intValue(pq, "__pageSize")); // 10 + 1 extra
        assertEquals(20, intValue(pq, "__offset"));   // (3 - 1) * 10
    }

    @Test
    void defaultsAreUsedWhenPageInfoMissing() {
        PreparedQuery pq = engine.build(QueryBuildRequest.builder()
                .template(TEMPLATE)
                .rules(RULES)
                .build());

        assertEquals(51, intValue(pq, "__pageSize")); // default 50 + 1 extra
        assertEquals(0, intValue(pq, "__offset"));
    }

    @Test
    void pageSizeIsCappedAtMaximum() {
        PreparedQuery pq = engine.build(QueryBuildRequest.builder()
                .template(TEMPLATE)
                .rules(RULES)
                .pageNo(1)
                .pageSize(5000) // too big -> capped at 200
                .build());

        assertEquals(201, intValue(pq, "__pageSize")); // 200 cap + 1 extra
    }

    // ---- a query that limits itself (e.g. "the top 5 doctors") ----
    //
    // The bug these guard: the engine used to append "order by ..." and
    // "limit ? offset ?" unconditionally. On a query already ending in LIMIT 5 that
    // produced "... LIMIT 5 limit ? offset ?", which MySQL rejects outright.

    private static final String TOP_5 =
            "SELECT d.first_name AS `First Name`, COUNT(a.id) AS `Appointment Count` " +
            "FROM doctors d JOIN appointments a ON a.doctor_id = d.id " +
            "WHERE d.is_deleted = false GROUP BY d.id " +
            "ORDER BY `Appointment Count` DESC LIMIT 5";

    @Test
    void aQueryThatLimitsItselfGetsNoExtraLimit() {
        PreparedQuery prepared = engine.build(QueryBuildRequest.builder()
                .template(TOP_5).filters(Map.of()).rules(List.of())
                .sortableColumns(List.of()).pageNo(1).pageSize(50).build());

        assertTrue(prepared.getSql().trim().toLowerCase().endsWith("limit 5"),
                "the query's own LIMIT must be the last thing in the statement");
        assertFalse(prepared.getSql().contains(":__pageSize"));
        assertFalse(prepared.getSql().contains(":__offset"));
    }

    @Test
    void aQueryThatLimitsItselfIsNotReSorted() {
        // sorting would land after the LIMIT, which is just as invalid
        PreparedQuery prepared = engine.build(QueryBuildRequest.builder()
                .template(TOP_5).filters(Map.of()).rules(List.of())
                .sortableColumns(List.of("First Name")).sortBy("First Name").sortOrder("asc")
                .pageNo(1).pageSize(50).build());

        assertFalse(prepared.getSql().toLowerCase().contains("order by `first name`"));
        assertTrue(prepared.getSql().trim().toLowerCase().endsWith("limit 5"));
    }

    @Test
    void anOrdinaryQueryStillGetsPaging() {
        PreparedQuery prepared = engine.build(QueryBuildRequest.builder()
                .template("SELECT name FROM specialties WHERE is_deleted = false")
                .filters(Map.of()).rules(List.of())
                .sortableColumns(List.of()).pageNo(1).pageSize(50).build());

        assertTrue(prepared.getSql().contains("limit :__pageSize offset :__offset"));
    }

    @Test
    void theWordLimitInsideATextValueDoesNotCount() {
        // 'limit 5' here is data, not a clause - the query still needs our paging
        PreparedQuery prepared = engine.build(QueryBuildRequest.builder()
                .template("SELECT name FROM specialties WHERE note = 'limit 5'")
                .filters(Map.of()).rules(List.of())
                .sortableColumns(List.of()).pageNo(1).pageSize(50).build());

        assertTrue(prepared.getSql().contains("limit :__pageSize offset :__offset"));
    }

    @Test
    void aLimitInsideASubqueryDoesNotCount() {
        // the LIMIT is in the middle, so the statement still needs paging appended
        PreparedQuery prepared = engine.build(QueryBuildRequest.builder()
                .template("SELECT x.name FROM (SELECT name FROM specialties LIMIT 3) x WHERE 1=1")
                .filters(Map.of()).rules(List.of())
                .sortableColumns(List.of()).pageNo(1).pageSize(50).build());

        assertTrue(prepared.getSql().contains("limit :__pageSize offset :__offset"));
    }
}
