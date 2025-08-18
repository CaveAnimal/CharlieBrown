package com.example.codetools;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AdminControllerSmokeTest {

    @Autowired
    MockMvc mvc;

    @Test
    public void smokeAdminEndpoints() throws Exception {
        // status should always return 200 and a size=... string
        String statusBody = mvc.perform(get("/api/admin/ann/status")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(statusBody).startsWith("size=");

        // health should return a JSON map; at least size key present
        String healthBody = mvc.perform(get("/api/admin/ann/health")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(healthBody).contains("size");

        // rebuild - should return 200 and a short message
        String rebuildBody = mvc.perform(post("/api/admin/ann/rebuild")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(rebuildBody).isNotBlank();

        // insert sample - should return count information
        String sampleBody = mvc.perform(post("/api/admin/ann/sample")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(sampleBody).contains("inserted sample");

    // persist to a temp path (no path param) should either persist or return no-ann message
    String persistBody = mvc.perform(post("/api/admin/ann/persist")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertThat(persistBody).satisfies(b -> {
        String s = (String) b;
        assertThat(s).satisfiesAnyOf(str -> assertThat((String) str).contains("persisted to"),
            str -> assertThat((String) str).contains("no ann service configured"));
    });
    }
}
