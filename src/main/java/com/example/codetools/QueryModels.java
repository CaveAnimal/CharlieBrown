package com.example.codetools;

import java.util.List;

public class QueryModels {

    public static class CodeSnippet {
        private String path;
        private String content;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
        // getters/setters
    }

    public static class QueryRequest {
        private String question;
        private List<String> paths;   // optional filter

        public String getQuestion() {
            return question;
        }
        public void setQuestion(String question) {
            this.question = question;
        }
        public List<String> getPaths() {
            return paths;
        }
        public void setPaths(List<String> paths) {
            this.paths = paths;
        }
    }

    public static class QueryResponse {
        private String answer;

        public String getAnswer() {
            return answer;
        }
        public void setAnswer(String answer) {
            this.answer = answer;
        }
    }
}
