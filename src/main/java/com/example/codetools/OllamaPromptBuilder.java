package com.example.codetools;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class OllamaPromptBuilder {

    public String build(String question, List<QueryModels.CodeSnippet> snippets) {
        StringBuilder prompt = new StringBuilder();
    prompt.append("You are a helpful programming assistant. Answer the question and explain any edge cases.\n");
    prompt.append("If the question is general knowledge or unrelated to the provided code snippets, answer directly from general knowledge and do not summarize the snippets.\n");
    prompt.append("Be concise: for general-knowledge questions give a short direct answer (one or two sentences).\n");
    prompt.append("For code-specific questions, give the best, concise answer and include small example snippets only if they clarify the solution. Avoid long-winded explanations.\n");
    prompt.append("Use the provided code snippets only when necessary to answer the question; if you reference a snippet, cite its file path.\n\n");
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
