package in.healthconnect.widgetengine.entity;

import jakarta.persistence.*;
import lombok.*;

// A filter defined once and reused by any widget that needs it.
//
// The point is that "the enterprises this user can see" is one question with one answer,
// not something each widget re-invents. A widget names the filter by id; this row knows
// how to fetch its choices.
//
// `source` is the query behind the dropdown. It returns two columns - id and value - and
// may take the same named parameters a widget takes, so a list can narrow itself to the
// current user or portfolio.
@Entity
@Table(name = "widget_filter")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WidgetFilter {

    // The name used in SQL as :id and quoted by widgets in their filter config, e.g.
    // "doctorId". Chosen by hand rather than generated, because it has to be readable
    // inside a query.
    @Id
    @Column(name = "id", length = 100, nullable = false)
    private String id;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    // columnDefinition rather than @Lob, matching Widget.sqlTemplate: @Lob on a String maps
    // to tinytext in this dialect, which is both too small for a query and not what the
    // column actually is.
    @Column(name = "source", nullable = false, columnDefinition = "TEXT")
    private String source;
}
