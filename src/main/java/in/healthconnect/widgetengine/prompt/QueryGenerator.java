package in.healthconnect.widgetengine.prompt;

// This is the "plug" for the PROMPT module.
// The idea: a user types a question in plain English (e.g. "how many active patients?")
// and something turns it into a MySQL query.
//
// The real implementation is NimQueryGenerator, which calls NVIDIA NIM.
// Because everything talks to this interface, swapping in a different AI provider
// changes nothing else in the app.
public interface QueryGenerator {

    // Turn a plain-English question into a SQL query string.
    String generateSql(String naturalLanguagePrompt);
}
