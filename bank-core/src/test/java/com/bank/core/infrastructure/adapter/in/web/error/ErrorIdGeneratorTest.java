package com.bank.core.infrastructure.adapter.in.web.error;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorIdGeneratorTest {

    private final ErrorIdGenerator generator = new ErrorIdGenerator();

    @Test
    void genere_un_id_au_format_attendu() {
        String id = generator.newErrorId();
        assertThat(id).matches("ERR-\\d{8}-[0-9A-HJKMNP-TV-Z]{8}");
    }

    @Test
    void n_utilise_pas_de_caracteres_ambigus() {
        String suffix = generator.newErrorId().substring("ERR-yyyyMMdd-".length());
        assertThat(suffix).matches("[^ILOU]+");
    }

    @Test
    void genere_des_ids_uniques() {
        Set<String> ids = IntStream.range(0, 10_000)
                .mapToObj(i -> generator.newErrorId())
                .collect(Collectors.toSet());
        assertThat(ids).hasSize(10_000);
    }
}
