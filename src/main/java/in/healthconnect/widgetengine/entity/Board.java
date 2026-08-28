package in.healthconnect.widgetengine.entity;

import in.healthconnect.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

// A "board" is a dashboard page. It has a name and a layout: the list of widgets on it,
// in order, each with a width (how many of the 3 columns it takes).
// The layout is stored as simple JSON text, e.g. [{"widgetId":12,"width":1}, ...].
@Entity
@Table(name = "board")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE board SET is_deleted = true WHERE id = ?")
public class Board extends BaseEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    // JSON list of items: [{ "widgetId": 12, "width": 1 }, ...]. Order = display order.
    @Column(name = "layout", columnDefinition = "TEXT")
    private String layout;
}
