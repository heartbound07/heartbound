package com.app.heartbound.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.app.heartbound.exceptions.shop.BadgeLimitException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Sanitizes error messages to prevent information leakage while preserving useful information for users.
     * This method removes potentially sensitive internal details while keeping user-friendly error descriptions.
     * 
     * @param originalMessage the original exception message
     * @return sanitized message safe for public consumption
     */
    private String sanitizeErrorMessage(String originalMessage) {
        if (originalMessage == null || originalMessage.isBlank()) {
            return "An error occurred while processing your request.";
        }
        
        // Remove common patterns that might leak sensitive information
        String sanitized = originalMessage
            // Remove SQL-related information
            .replaceAll("(?i)\\b(sql|database|table|column|constraint|foreign key|primary key)\\b.*", "Database operation failed.")
            // Remove file path information
            .replaceAll("(?i)\\b[a-zA-Z]:[\\\\|/][^\\s]*", "[file path]")
            // Remove class names and stack trace hints
            .replaceAll("(?i)\\b[a-zA-Z]+\\.([a-zA-Z]+\\.)*[A-Z][a-zA-Z]*Exception\\b", "System error")
            // Remove connection strings and URLs with credentials
            .replaceAll("(?i)\\b(jdbc:|http://|https://)[^\\s]*", "[connection info]")
            // Keep user-friendly messages that start with common patterns
            .trim();
        
        // If message was heavily sanitized or is too technical, provide a generic message
        if (sanitized.length() < 10 || sanitized.toLowerCase().contains("exception") || 
            sanitized.toLowerCase().contains("error") && sanitized.length() > 100) {
            return "An error occurred while processing your request. Please try again or contact support if the issue persists.";
        }
        
        return sanitized;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex,
                                                                HttpServletRequest request) {
        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                sanitizeErrorMessage(ex.getMessage()),
                request.getRequestURI()
        );
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(TradeNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTradeNotFoundException(TradeNotFoundException ex,
                                                                    HttpServletRequest request) {
        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                sanitizeErrorMessage(ex.getMessage()),
                request.getRequestURI()
        );
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InvalidTradeActionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTradeActionException(InvalidTradeActionException ex,
                                                                          HttpServletRequest request) {
        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                sanitizeErrorMessage(ex.getMessage()),
                request.getRequestURI()
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ItemEquippedException.class)
    public ResponseEntity<ErrorResponse> handleItemEquippedException(ItemEquippedException ex,
                                                                          HttpServletRequest request) {
        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                sanitizeErrorMessage(ex.getMessage()),
                request.getRequestURI()
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InsufficientItemsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientItemsException(InsufficientItemsException ex,
                                                                          HttpServletRequest request) {
        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                sanitizeErrorMessage(ex.getMessage()),
                request.getRequestURI()
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UnauthorizedOperationException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedOperationException ex,
                                                            HttpServletRequest request) {
        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                sanitizeErrorMessage(ex.getMessage()),
                request.getRequestURI()
        );
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(RuntimeException ex,
                                                          HttpServletRequest request) {
        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                sanitizeErrorMessage(ex.getMessage()),
                request.getRequestURI()
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex,
                                                                HttpServletRequest request) {
        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                sanitizeErrorMessage(ex.getMessage()),
                request.getRequestURI()
        );
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex,
                                                                  HttpServletRequest request) {
        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                sanitizeErrorMessage(ex.getMessage()),
                request.getRequestURI()
        );
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTokenException(InvalidTokenException ex,
                                                                HttpServletRequest request) {
        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                HttpStatus.UNAUTHORIZED.value(),
                "Invalid Token",
                sanitizeErrorMessage(ex.getMessage()),
                request.getRequestURI()
        );
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceededException(RateLimitExceededException ex,
                                                          HttpServletRequest request) {
        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Too Many Requests",
                sanitizeErrorMessage(ex.getMessage()),
                request.getRequestURI()
        );
        
        // Extract retry-after time from the message if available
        String retryAfterSeconds = extractRetryAfterFromMessage(ex.getMessage());
        
        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("X-RateLimit-Limit", "Rate limit exceeded")
                .header("X-RateLimit-Remaining", "0");
        
        if (retryAfterSeconds != null) {
            responseBuilder.header("Retry-After", retryAfterSeconds);
        }
        
        return responseBuilder.body(response);
    }
    
    /**
     * Extracts retry-after time from rate limit exception message
     */
    private String extractRetryAfterFromMessage(String message) {
        try {
            // Look for pattern "try again in X seconds"
            if (message != null && message.contains("try again in")) {
                String[] parts = message.split("try again in ");
                if (parts.length > 1) {
                    String secondsPart = parts[1].split(" ")[0];
                    return secondsPart;
                }
            }
        } catch (Exception e) {
            // If parsing fails, don't add retry-after header
        }
        return null;
    }

    @ExceptionHandler(BadgeLimitException.class)
    public ResponseEntity<ErrorResponse> handleBadgeLimitException(BadgeLimitException ex,
                                                          HttpServletRequest request) {
        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Badge Limit Exceeded",
                sanitizeErrorMessage(ex.getMessage()),
                request.getRequestURI()
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // Structured error response structure.
    public static class ErrorResponse {
        private Instant timestamp;
        private int status;
        private String error;
        private String message;
        private String path;

        public ErrorResponse(Instant timestamp, int status, String error, String message, String path) {
            this.timestamp = timestamp;
            this.status = status;
            this.error = error;
            this.message = message;
            this.path = path;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public int getStatus() {
            return status;
        }

        public String getError() {
            return error;
        }

        public String getMessage() {
            return message;
        }

        public String getPath() {
            return path;
        }
    }
}
