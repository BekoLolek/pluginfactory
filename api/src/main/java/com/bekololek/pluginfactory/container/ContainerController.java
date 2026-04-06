package com.bekololek.pluginfactory.container;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/containers")
@RequiredArgsConstructor
public class ContainerController {

    private final ContainerPoolManager poolManager;

    @GetMapping
    public ResponseEntity<Map<String, Integer>> getPoolStatus() {
        return ResponseEntity.ok(poolManager.getPoolStatus());
    }

    @PostMapping("/scale")
    public ResponseEntity<Map<String, Integer>> scalePool(@RequestBody ScaleRequest request) {
        if (request.warmBuild() != null) {
            for (int i = poolManager.getPoolStatus().get("warmBuild"); i < request.warmBuild(); i++) {
                poolManager.claimContainer(DockerService.ContainerType.BUILD);
            }
        }
        if (request.warmTest() != null) {
            for (int i = poolManager.getPoolStatus().get("warmTest"); i < request.warmTest(); i++) {
                poolManager.claimContainer(DockerService.ContainerType.TEST);
            }
        }
        return ResponseEntity.ok(poolManager.getPoolStatus());
    }

    public record ScaleRequest(Integer warmBuild, Integer warmTest) {}
}
