package com.bank.core.infrastructure.adapter.in.web.error;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Génère un identifiant d'erreur unique, court, horodaté et non devinable.
 * <p>
 * Format : {@code ERR-yyyyMMdd-XXXXXXXX} où le suffixe utilise l'alphabet
 * Crockford Base32 (sans I, L, O, U) afin d'être dicté sans ambiguïté par
 * téléphone au support.
 */
@Component
public class ErrorIdGenerator {

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int SUFFIX_LENGTH = 8;

    private final SecureRandom random = new SecureRandom();

    public String newErrorId() {
        StringBuilder sb = new StringBuilder("ERR-")
                .append(LocalDate.now().format(DAY))
                .append('-');
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
