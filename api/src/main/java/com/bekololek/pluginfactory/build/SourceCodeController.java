package com.bekololek.pluginfactory.build;

import com.bekololek.pluginfactory.build.dto.CreateSourceCodeRequestRequest;
import com.bekololek.pluginfactory.build.dto.SourceCodeRequestDto;
import com.bekololek.pluginfactory.common.util.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/source-code")
@RequiredArgsConstructor
public class SourceCodeController {

    private final SourceCodeRequestService sourceCodeRequestService;

    @PostMapping("/request")
    public ResponseEntity<SourceCodeRequestDto> requestSourceCode(
            @Valid @RequestBody CreateSourceCodeRequestRequest request,
            HttpServletRequest httpRequest) {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        String clientIp = httpRequest.getRemoteAddr();

        SourceCodeRequestDto dto = sourceCodeRequestService.requestSourceCode(
                userId, request.artifactId(), request.licenseVersion(), clientIp);

        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{requestId}/fulfill")
    public ResponseEntity<SourceCodeRequestDto> fulfillRequest(@PathVariable UUID requestId) {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        SourceCodeRequestDto dto = sourceCodeRequestService.fulfillRequest(requestId, userId);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/{requestId}/download")
    public ResponseEntity<byte[]> downloadSourceCode(@PathVariable UUID requestId) {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        byte[] zipBytes = sourceCodeRequestService.downloadSourceCode(requestId, userId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"source-watermarked.zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(zipBytes.length)
                .body(zipBytes);
    }
}
