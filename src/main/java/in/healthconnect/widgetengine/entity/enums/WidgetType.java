package in.healthconnect.widgetengine.entity.enums;

// This tells the screen (frontend) HOW to show the widget's data.
// The backend does not draw anything - it only stores this choice.
//
//   COUNT = show one single number (example: "Active patients: 128")
//   TABLE = show rows in a table
//   BAR   = show a bar chart
//   LINE  = show a line chart
//   PIE   = show a pie chart
public enum WidgetType {
    COUNT,
    TABLE,
    BAR,
    LINE,
    PIE
}
