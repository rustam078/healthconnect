package in.healthconnect.widgetengine.entity.enums;

// Whether a widget is ready to use.
//   DRAFT    = just created (e.g. by the AI); a person should look at it first.
//   APPROVED = checked and ready to be used normally.
// Normal widgets you create by hand are APPROVED right away. AI-generated widgets
// start as DRAFT so you can review the query before trusting it.
public enum WidgetStatus {
    DRAFT,
    APPROVED
}
