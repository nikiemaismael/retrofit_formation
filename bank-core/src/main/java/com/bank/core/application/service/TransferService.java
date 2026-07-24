package com.bank.core.application.service;

import com.bank.core.application.port.in.TransferCommand;
import com.bank.core.application.port.in.TransferUseCase;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Implémentation de démonstration du cas d'usage de virement.
 * Dans une vraie application, orchestre les ports de sortie (comptes, écritures)
 * et lève des {@code BusinessException} du domaine selon les règles métier.
 */
@Service
public class TransferService implements TransferUseCase {

    @Override
    public String execute(TransferCommand command) {
        // Règles métier réelles à implémenter ici.
        return "OP-" + UUID.randomUUID();
    }
}
