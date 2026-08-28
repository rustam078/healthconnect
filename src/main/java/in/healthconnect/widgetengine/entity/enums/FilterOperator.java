package in.healthconnect.widgetengine.entity.enums;

import java.util.Arrays;
import java.util.Optional;

// This is the LIST OF ALLOWED comparison operators for a filter.
//
// Why it matters (the safety idea):
// A filter compares a column to a value, like  status = 'ACTIVE'  or  age >= 18.
// The user tells us which comparison they want by sending a short word like "eq" or "gte".
// We NEVER put the user's raw text into the SQL. Instead we look it up in this list and
// use OUR fixed symbol (=, >=, IN ...). If the word is not in this list, we reject it.
// This blocks a common attack where a user tries to sneak bad text into the query.
//
// Each operator also remembers two things about the value it needs:
//   isList = true  -> the value is a list of many values (used by IN / NOT IN)
//   isLike = true  -> the value is wrapped with % signs for text search (used by LIKE)
public enum FilterOperator {

    // key (what the user sends) , symbol (what we put in SQL) , isList , isLike
    EQ("eq", "=", false, false),
    NE("ne", "<>", false, false),
    GT("gt", ">", false, false),
    GTE("gte", ">=", false, false),
    LT("lt", "<", false, false),
    LTE("lte", "<=", false, false),
    IN("in", "IN", true, false),
    NOT_IN("notin", "NOT IN", true, false),
    LIKE("like", "LIKE", false, true),
    NOT_LIKE("notlike", "NOT LIKE", false, true);

    private final String key;     // the short word the user sends, e.g. "gte"
    private final String symbol;  // the real SQL text we use, e.g. ">=". Always from here, never from the user.
    private final boolean list;   // true only for IN / NOT IN (value is many values)
    private final boolean like;   // true only for LIKE / NOT LIKE (value becomes %value%)

    FilterOperator(String key, String symbol, boolean list, boolean like) {
        this.key = key;
        this.symbol = symbol;
        this.list = list;
        this.like = like;
    }

    public String getKey() {
        return key;
    }

    public String getSymbol() {
        return symbol;
    }

    public boolean isList() {
        return list;
    }

    public boolean isLike() {
        return like;
    }

    // Turn the user's word (like "in") into one of the operators above.
    // If the word is unknown, we return "empty" so the caller can reject the request.
    public static Optional<FilterOperator> fromKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String normalized = key.trim().toLowerCase();
        return Arrays.stream(values())
                .filter(op -> op.key.equals(normalized))
                .findFirst();
    }
}
