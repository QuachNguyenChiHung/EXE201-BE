package com.ailogis.api.controller;

import com.ailogis.api.dto.CertDTO;
import com.ailogis.api.service.CertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/certs")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class CertController {

    private final CertService certService;

    @GetMapping
    public ResponseEntity<List<CertDTO>> getCerts(
            @RequestParam(required = false) String label,
            @RequestParam(required = false) String labelDesc) {
        return ResponseEntity.ok(certService.getCerts(label, labelDesc));
    }

    @PostMapping
    public ResponseEntity<CertDTO> createCert(@RequestBody CertDTO dto) {
        return ResponseEntity.ok(certService.createCert(dto));
    }

    @PatchMapping("/{certID}")
    public ResponseEntity<CertDTO> updateCert(
            @PathVariable Long certID,
            @RequestBody CertDTO dto) {
        return ResponseEntity.ok(certService.updateCert(certID, dto));
    }

    @PostMapping("/upload/{certID}")
    public ResponseEntity<Map<String, String>> uploadPdf(
            @PathVariable Long certID,
            @RequestPart("file") MultipartFile file) {
        String pdfUrl = certService.uploadPdf(certID, file);
        return ResponseEntity.ok(Map.of("pdfUrl", pdfUrl));
    }
}