package com.bank.core.infrastructure.adapter.in.web.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityErrorIT {

    @Autowired
    private MockMvc mvc;

    @Test
    @WithAnonymousUser
    void non_authentifie_recoit_401_json_generique() throws Exception {
        mvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("BANK-2000"))
                .andExpect(jsonPath("$.errorId", startsWith("ERR-")))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void authentifie_sans_droit_recoit_403_json() throws Exception {
        mvc.perform(delete("/api/v1/admin/accounts/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BANK-2001"))
                .andExpect(jsonPath("$.errorId", startsWith("ERR-")));
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void authentifie_accede_a_ses_ressources() throws Exception {
        mvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk());
    }
}
