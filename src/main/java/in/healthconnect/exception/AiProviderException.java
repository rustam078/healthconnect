package in.healthconnect.exception;

// Thrown when the upstream AI provider (NVIDIA NIM) fails or answers with something we
// cannot use - a rejected request, an unknown model, or a reply with no SQL in it.
//
// It has its own type so GlobalExceptionHandler can pass the message through instead of
// hiding it behind the catch-all "An error occurred". The message tells the user which
// setting to look at; the raw provider response is logged server-side, never returned.
//
// Maps to 502: the request was fine, the thing we depend on was not.
public class AiProviderException extends RuntimeException {
    public AiProviderException(String message) {
        super(message);
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
