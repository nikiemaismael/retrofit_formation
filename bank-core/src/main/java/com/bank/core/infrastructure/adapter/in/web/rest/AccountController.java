package com.bank.core.infrastructure.adapter.in.web.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Contrôleur minimal servant à illustrer les règles de sécurité
 * (authentification requise, rôle ADMIN sur la partie /admin).
 */
@RestController
@RequestMapping("/api/v1")
public class AccountController {

    @GetMapping("/accounts")
    public ResponseEntity<List<Map<String, String>>> list() {
        return ResponseEntity.ok(List.of(Map.of("iban", "FR76****0001")));
    }

    @DeleteMapping("/admin/accounts/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        return ResponseEntity.noContent().build();
    }
}
