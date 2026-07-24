package com.bank.core.infrastructure.adapter.in.web.error;

import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiErrorFactoryTest {

    private final MessageSource messageSource = mock(MessageSource.class);

    @Test
    void resout_le_message_i18n_et_ne_fuit_pas_l_exception() {
        when(messageSource.getMessage(eq("error.funds.insufficient"), any(), any(), any()))
                .thenReturn("Solde insuffisant.");
        ApiErrorFactory factory = new ApiErrorFactory(messageSource, prod());

        ApiError err = factory.create("ERR-20260724-3F9K2A7Q",
                ErrorCode.INSUFFICIENT_FUNDS, "/api/v1/transfers", Locale.FRENCH);

        assertThat(err.message()).isEqualTo("Solde insuffisant.");
        assertThat(err.code()).isEqualTo("BANK-1002");
        assertThat(err.status()).isEqualTo(422);
        assertThat(err.errorId()).isEqualTo("ERR-20260724-3F9K2A7Q");
        assertThat(err.debug()).isNull();
    }

    @Test
    void en_prod_le_champ_debug_reste_null_meme_avec_exception_technique() {
        when(messageSource.getMessage(any(), any(), any(), any())).thenReturn("Erreur interne.");
        ApiErrorFactory factory = new ApiErrorFactory(messageSource, prod());

        ApiError err = factory.create("ERR-1", ErrorCode.INTERNAL_ERROR, "/x", Locale.FRENCH,
                new Object[0], null, new IllegalStateException("ORA-03113 socket fermé"));

        assertThat(err.debug()).isNull();
    }

    @Test
    void en_dev_le_champ_debug_expose_le_message_technique() {
        when(messageSource.getMessage(any(), any(), any(), any())).thenReturn("Erreur interne.");
        ApiErrorFactory factory = new ApiErrorFactory(messageSource, dev());

        ApiError err = factory.create("ERR-1", ErrorCode.INTERNAL_ERROR, "/x", Locale.FRENCH,
                new Object[0], null, new IllegalStateException("ORA-03113 socket fermé"));

        assertThat(err.debug()).contains("ORA-03113");
    }

    @Test
    void le_modele_expose_ne_contient_aucun_champ_technique() {
        String[] names = Arrays.stream(ApiError.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toArray(String[]::new);
        assertThat(names).doesNotContain("stackTrace", "exception", "cause", "trace");
    }

    private ErrorProperties prod() {
        return new ErrorProperties(false, "/error.xhtml");
    }

    private ErrorProperties dev() {
        return new ErrorProperties(true, "/error.xhtml");
    }
}
