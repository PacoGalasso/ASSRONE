// controller/EventController.java — remplace le fichier entier
package ASSRONE.backend.controller;

import ASSRONE.backend.dto.CreateEventRequest;
import ASSRONE.backend.dto.EventDto;
import ASSRONE.backend.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping("/upcoming")
    public List<EventDto> getUpcoming() {
        return eventService.getUpcomingEvents();
    }

    @GetMapping
    public List<EventDto> getAll() {
        return eventService.getAllEvents();
    }

    @PostMapping
    public ResponseEntity<EventDto> create(@Valid @RequestBody CreateEventRequest request) {
        EventDto created = eventService.createEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}