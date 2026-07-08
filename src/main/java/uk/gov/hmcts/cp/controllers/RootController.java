package uk.gov.hmcts.cp.controllers;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@Slf4j
public class RootController {

    @GetMapping("/")
    public ResponseEntity<String> getRoot() {
        log.info("START");
        return ResponseEntity.ok("Welcome to service-cp-crime-defendant-details");
    }
}