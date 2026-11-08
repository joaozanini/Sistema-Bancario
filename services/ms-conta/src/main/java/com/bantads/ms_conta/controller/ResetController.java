package com.bantads.ms_conta.controller;

import com.bantads.ms_conta.service.ResetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ResetController {

    private final ResetService service;

    public ResetController(ResetService service) {
        this.service = service;
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> reset() {
        service.reset();
        return ResponseEntity.noContent().build();
    }
}
