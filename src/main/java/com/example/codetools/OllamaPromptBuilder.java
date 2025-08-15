package com.example.codetools;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OllamaPromptBuilder {

    public String build(String question, List<QueryModels.CodeSnippet> snippets) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a helpful programming assistant. Answer the question and explain any edge cases.\n\n");
        prompt.append("Question: ").append(question).append("\n\n");
        if (snippets != null && !snippets.isEmpty()) {
            for (QueryModels.CodeSnippet s : snippets) {
                prompt.append("File: ").append(s.getPath()).append("\n");
                String lang = "text";
                if (s.getPath().toLowerCase().endsWith(".java")) lang = "java";
                else if (s.getPath().toLowerCase().endsWith(".js")) lang = "javascript";
                prompt.append("```").append(lang).append("\n");
                prompt.append(s.getContent()).append("\n");
                prompt.append("```\n\n");
            }
        }
        return prompt.toString().trim();
    }
}
