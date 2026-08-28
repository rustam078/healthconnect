package in.healthconnect.widgetengine.entity.enums;

// A widget belongs to ONE of these 3 groups.
// This just tells us how the widget is meant to be used.
//
//   WIDGET      = a box shown on a dashboard (like a small report card).
//                 When a user builds a new board, we suggest these to them.
//   INTEGRATION = a saved query we expose as a simple API. You call it and get data back.
//                 It saves us from writing a new API by hand every time.
//   PROMPT      = user types a question in plain English, AI (Gemini) writes the query,
//                 then we run it. Not built yet - we only leave a spot for it for now.
//
// The engine that runs the query is the SAME for all 3. Only the "how you call it" changes.
public enum WidgetModule {
    WIDGET,
    INTEGRATION,
    PROMPT
}
