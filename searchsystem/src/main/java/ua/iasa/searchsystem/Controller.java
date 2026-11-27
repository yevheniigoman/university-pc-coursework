package ua.iasa.searchsystem;

import ua.iasa.searchsystem.dto.HealthDto;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

@RestController
public class Controller {
    @GetMapping("/api/health")
    public ResponseEntity<HealthDto> health() {
        var dto = new HealthDto("ok", false);
        return ResponseEntity.status(HttpStatus.OK).body(dto);
    }
}
