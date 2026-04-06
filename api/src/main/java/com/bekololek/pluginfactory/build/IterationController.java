package com.bekololek.pluginfactory.build;

import com.bekololek.pluginfactory.build.dto.BuildIterationDto;
import com.bekololek.pluginfactory.build.dto.IterationRequest;
import com.bekololek.pluginfactory.common.util.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/builds/{sessionId}")
@RequiredArgsConstructor
public class IterationController {

    private final IterationService iterationService;
    private final BuildSessionService buildSessionService;

    @GetMapping("/iterations")
    public ResponseEntity<List<BuildIterationDto>> listIterations(@PathVariable UUID sessionId) {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        buildSessionService.getSession(sessionId, userId); // ownership check
        List<BuildIterationDto> iterations = iterationService.listIterations(sessionId).stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(iterations);
    }

    @PostMapping("/iterate")
    public ResponseEntity<BuildIterationDto> requestIteration(
            @PathVariable UUID sessionId,
            @Valid @RequestBody IterationRequest request) {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        BuildIteration iteration = iterationService.requestIteration(sessionId, userId, request.feedback());
        return ResponseEntity.ok(toDto(iteration));
    }

    private BuildIterationDto toDto(BuildIteration iteration) {
        return new BuildIterationDto(
                iteration.getId(),
                iteration.getSessionId(),
                iteration.getIterationNumber(),
                iteration.getStatus(),
                iteration.getTrigger(),
                iteration.getStartedAt(),
                iteration.getCompletedAt()
        );
    }
}
