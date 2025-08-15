package com.example.codetools;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class QueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OllamaClient ollamaClient;

    @MockBean
    private FileScanner fileScanner;

    @Test
    public void testGetQueryReturnsMockedAnswer() throws Exception {
        List<QueryModels.CodeSnippet> snippets = new ArrayList<>();
        QueryModels.CodeSnippet s = new QueryModels.CodeSnippet();
        s.setPath("webpack.config.js");
        s.setContent("// sample content");
        snippets.add(s);

        when(fileScanner.fetchSnippets(null, 3)).thenReturn(snippets);
        when(ollamaClient.queryModel(eq("what does this do?"), anyList())).thenReturn("mocked answer");

        mockMvc.perform(get("/api/query").param("question", "what does this do?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("mocked answer"));
    }

    @Test
    public void testPostQueryReturnsMockedAnswer() throws Exception {
        List<QueryModels.CodeSnippet> snippets = new ArrayList<>();
        QueryModels.CodeSnippet s = new QueryModels.CodeSnippet();
        s.setPath("webpack.config.js");
        s.setContent("// sample");
        snippets.add(s);

        when(fileScanner.fetchSnippets(anyList(), eq(3))).thenReturn(snippets);
        when(ollamaClient.queryModel(eq("what does this do?"), anyList())).thenReturn("post-mock");

        String body = "{\"question\":\"what does this do?\",\"paths\":[\"webpack.config.js\"]}";

        mockMvc.perform(post("/api/query").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("post-mock"));
    }
}
