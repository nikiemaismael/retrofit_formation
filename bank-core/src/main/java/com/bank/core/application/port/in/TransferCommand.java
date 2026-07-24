package com.bank.core.application.port.in;

import java.math.BigDecimal;

/** Commande applicative (entrée du cas d'usage de virement). */
public record TransferCommand(String sourceIban, String targetIban, BigDecimal amount) {
}
