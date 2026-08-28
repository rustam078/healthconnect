package in.healthconnect.widgetengine.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// A history record. We add ONE row here every time a widget is run.
// It answers: which widget ran, with what filters, how long it took,
// how many rows came back, and did it work or fail.
// This is useful for checking problems later.
//
// Note: this does NOT extend BaseEntity, because a history record is never
// edited or deleted - we only ever add new ones. So it just needs its own id and a time.
@Entity
@Table(name = "widget_execution_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WidgetExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // Which widget was run (its number and its code). We copy both so this
    // record still makes sense on its own, even if the widget changes later.
    @Column(name = "widget_id")
    private Integer widgetId;

    @Column(name = "widget_code", length = 150)
    private String widgetCode;

    @Column(name = "module", length = 20)
    private String module;

    // The filters the user sent, saved as JSON text (so we can see what was asked).
    @Column(name = "params_json", columnDefinition = "TEXT")
    private String paramsJson;

    // How many rows the query returned.
    @Column(name = "row_count")
    private Integer rowCount;

    // How long the query took, in milliseconds (1000 ms = 1 second).
    @Column(name = "duration_ms")
    private Long durationMs;

    // Did it work? true = success, false = failed.
    @Column(name = "success", nullable = false)
    private boolean success;

    // If it failed, a short reason (kept short on purpose).
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    // The exact time it ran.
    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    // Just before saving, if no time was set, set it to "now".
    @PrePersist
    void onPersist() {
        if (executedAt == null) {
            executedAt = Instant.now();
        }
    }
}
