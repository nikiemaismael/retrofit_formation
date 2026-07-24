package com.bank.core.infrastructure.adapter.in.web.rest;

import com.bank.core.application.port.in.TransferCommand;
import com.bank.core.application.port.in.TransferUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferUseCase transferUseCase;

    public TransferController(TransferUseCase transferUseCase) {
        this.transferUseCase = transferUseCase;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> transfer(@Valid @RequestBody TransferRequest request) {
        String reference = transferUseCase.execute(
                new TransferCommand(request.sourceIban(), request.targetIban(), request.amount()));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("reference", reference));
    }
}
