package lt.printerrental.notifications;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class NotificationExceptionHandler {
  @ExceptionHandler(NotificationNotFoundException.class)
  ProblemDetail notFound(NotificationNotFoundException error) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail invalid(IllegalArgumentException error) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
  }
}
