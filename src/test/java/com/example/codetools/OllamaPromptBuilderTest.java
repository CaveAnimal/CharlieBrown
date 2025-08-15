package com.example.codetools;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class OllamaPromptBuilderTest {

    @Test
    public void buildsPromptWithSnippets() {
        OllamaPromptBuilder b = new OllamaPromptBuilder();
        List<QueryModels.CodeSnippet> snippets = new ArrayList<>();
        QueryModels.CodeSnippet s = new QueryModels.CodeSnippet();
        s.setPath("src/main/java/foo/Bar.java");
        s.setContent("public class Bar {}\n");
        snippets.add(s);

        String prompt = b.build("What does this do?", snippets);
        assertThat(prompt).contains("Question: What does this do?");
        assertThat(prompt).contains("File: src/main/java/foo/Bar.java");
        assertThat(prompt).contains("```java");
    }

    @Test
    public void buildsPromptWithoutSnippets() {
        OllamaPromptBuilder b = new OllamaPromptBuilder();
        String p = b.build("Q?", null);
        assertThat(p).contains("Question: Q?");
    }
}
