package com.servertest.products;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Access rules of the API. Tokens are mocked: signature, issuer and audience
 * validation is Spring Security's job and is configured in application.yml.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityRulesTest {

    @Autowired
    MockMvc mvc;

    static RequestPostProcessor token(String... scopes) {
        SimpleGrantedAuthority[] authorities = new SimpleGrantedAuthority[scopes.length];
        for (int i = 0; i < scopes.length; i++) {
            authorities[i] = new SimpleGrantedAuthority("SCOPE_" + scopes[i]);
        }
        return jwt().authorities(authorities);
    }

    @Test
    void noTokenIsRejectedWith401ProblemDetail() throws Exception {
        mvc.perform(get("/api/products/units"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/products/categories").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthCheckIsPublic() throws Exception {
        mvc.perform(get("/api/products/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void readScopeAllowsReads() throws Exception {
        mvc.perform(get("/api/products/units").with(token("products:read"))).andExpect(status().isOk());
        mvc.perform(get("/api/products").with(token("products:read"))).andExpect(status().isOk());
    }

    @Test
    void readScopeCannotWrite() throws Exception {
        mvc.perform(post("/api/products/categories").with(token("products:read"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Not allowed\"}"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer error=\"insufficient_scope\"")))
                .andExpect(jsonPath("$.status").value(403));
        mvc.perform(delete("/api/products/00000000-0000-0000-0000-000000000000").with(token("products:read")))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenWithoutProductScopesIsForbidden() throws Exception {
        mvc.perform(get("/api/products/units").with(token("stock:read"))).andExpect(status().isForbidden());
    }

    @Test
    void writeScopeCanWrite() throws Exception {
        mvc.perform(post("/api/products/categories").with(token("products:read", "products:write"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Allowed\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void pathsOutsideTheApiAreDenied() throws Exception {
        mvc.perform(get("/internal/anything").with(token("products:read", "products:write")))
                .andExpect(status().isForbidden());
    }
}
