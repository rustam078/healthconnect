package in.healthconnect.widgetengine.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRawValue;
import in.healthconnect.entity.BaseEntity;
import in.healthconnect.widgetengine.entity.enums.WidgetModule;
import in.healthconnect.widgetengine.entity.enums.WidgetStatus;
import in.healthconnect.widgetengine.entity.enums.WidgetType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

// A "widget" is one saved query plus a few details about it.
// One row in this table = one dashboard box OR one simple data API.
// The engine reads this row, fills in the filters, runs the query, and returns the data.
//
// It extends BaseEntity, so it automatically gets: id, created/updated time,
// and a "soft delete" flag (we mark rows as deleted instead of really removing them).
@Entity
@Table(
        name = "widget",
        // "code" must be unique - no two widgets can share the same code.
        uniqueConstraints = @UniqueConstraint(name = "uk_widget_code", columnNames = "code")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
// When someone "deletes" a widget, don't really delete it - just set is_deleted = true.
@SQLDelete(sql = "UPDATE widget SET is_deleted = true WHERE id = ?")
public class Widget extends BaseEntity {

    // A short, readable name used to find this widget, e.g. "active-patients-count".
    // We look widgets up by this instead of by number, so URLs stay easy to read.
    @Column(name = "code", nullable = false, unique = true, length = 150)
    private String code;

    // The title shown to the user.
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    // Some notes about the widget (or the plain-English question that made it).
    @Column(name = "description", length = 1000)
    private String description;

    // Which group this widget is in (WIDGET / INTEGRATION / PROMPT). Saved as text.
    @Enumerated(EnumType.STRING)
    @Column(name = "module", nullable = false, length = 20)
    private WidgetModule module;

    // How to draw it (COUNT / TABLE / BAR ...). Saved as text.
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private WidgetType type;

    // The actual SQL query, with blanks to fill in later:
    //   :name     = a value we will fill in safely
    //   {{name}}  = an operator (=, IN, LIKE ...) we will fill in from our safe list
    // @JsonIgnore = never send this query to the browser (keep it hidden/safe).
    @JsonIgnore
    @Column(name = "sql_template", nullable = false, columnDefinition = "TEXT")
    private String sqlTemplate;

    // The list of filters this widget offers, saved as JSON text. Example:
    //   { "filters": [ { "key":"status", "label":"Status", "operators":["eq","in"] } ],
    //     "sortableColumns": ["Patient","Status"] }
    // We store it as plain text so it works on any database. We read it with a JSON tool when needed.
    // @JsonRawValue = when we send this to the browser, send it as real JSON (not as a quoted string).
    @JsonRawValue
    @Column(name = "filters", columnDefinition = "TEXT")
    private String filters;

    // Turn a widget on or off without deleting it. Defaults to on (true).
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    // DRAFT (needs review) or APPROVED (ready). AI-generated widgets start as DRAFT.
    // Stored as text. Older rows may be null, which we treat as APPROVED.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private WidgetStatus status;
}
