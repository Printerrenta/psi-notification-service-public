package lt.printerrental.notifications;

import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
  private final NotificationService service;

  public NotificationController(NotificationService service) {
    this.service = service;
  }

  @GetMapping
  public NotificationService.Page list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list(page, size);
  }

  @PatchMapping("/{id}/read")
  public Notification read(@PathVariable UUID id) {
    return service.read(id);
  }
}
