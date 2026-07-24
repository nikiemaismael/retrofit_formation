package com.bank.core.infrastructure.adapter.in.web.error;

import com.bank.core.application.port.in.TransferUseCase;
import com.bank.core.domain.exception.InsufficientFundsException;
import com.bank.core.infrastructure.adapter.in.web.rest.TransferController;
import com.bank.core.infrastructure.config.MessageConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TransferController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalRestExceptionHandler.class, ApiErrorFactory.class, ErrorIdGenerator.class,
        ErrorLogger.class, MessageConfig.class})
@EnableConfigurationProperties(ErrorProperties.class)
class GlobalRestExceptionHandlerIT {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private TransferUseCase transferUseCase;

    @Test
    void business_exception_est_mappee_proprement() throws Exception {
        when(transferUseCase.execute(any()))
                .thenThrow(new InsufficientFundsException("FR76XXXX"));

        mvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceIban":"FR76AAAA","targetIban":"FR7612345678901","amount":100}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BANK-1002"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.errorId", startsWith("ERR-")))
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.debug").doesNotExist());
    }

    @Test
    void exception_technique_devient_500_generique_sans_detail() throws Exception {
        when(transferUseCase.execute(any()))
                .thenThrow(new IllegalStateException("connexion JDBC fermée: ORA-03113"));

        mvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceIban":"FR76AAAA","targetIban":"FR7612345678901","amount":100}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("BANK-9000"))
                .andExpect(jsonPath("$.message", not(containsString("ORA-"))))
                .andExpect(jsonPath("$.errorId", startsWith("ERR-")));
    }

    @Test
    void validation_retourne_400_avec_violations() throws Exception {
        mvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceIban":"","targetIban":"BAD","amount":-5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BANK-1000"))
                .andExpect(jsonPath("$.violations").isArray());
    }

    @Test
    void json_malforme_retourne_400() throws Exception {
        mvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ ceci n'est pas du json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BANK-1006"));
    }

    @Test
    void data_integrity_est_masquee_en_409() throws Exception {
        when(transferUseCase.execute(any()))
                .thenThrow(new DataIntegrityViolationException(
                        "ERROR: duplicate key value violates unique constraint \"uk_operation\""));

        mvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceIban":"FR76AAAA","targetIban":"FR7612345678901","amount":100}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BANK-1004"))
                .andExpect(jsonPath("$.message", not(containsString("constraint"))));
    }

    @ParameterizedTest
    @MethodSource("exceptionsTechniques")
    void aucune_reponse_ne_contient_de_detail_technique(RuntimeException thrown) throws Exception {
        when(transferUseCase.execute(any())).thenThrow(thrown);

        mvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceIban":"FR76AAAA","targetIban":"FR7612345678901","amount":100}
                                """))
                .andExpect(jsonPath("$.message",
                        matchesPattern("(?i)^(?!.*(ORA-|SQLException|at com\\.|Caused by|\\.java:)).*$")));
    }

    static Stream<Arguments> exceptionsTechniques() {
        return Stream.of(
                Arguments.of(new IllegalStateException("ORA-01017 invalid credentials")),
                Arguments.of(new NullPointerException("at com.bank.Service.run(Service.java:42)")),
                Arguments.of(new DataIntegrityViolationException("SQLException: constraint violation")),
                Arguments.of(new RuntimeException("Caused by: java.sql.SQLException"))
        );
    }
}
