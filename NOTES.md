```markdown
Developer stack (minimal):

- Language: Java 21
- Build: Maven (Apache Maven 3.9.x recommended)
- Framework: Spring Boot 3.x (project uses 3.2.7)
- Persistence: H2 (file-based) via Spring Data JPA / Hibernate
- Approximate vector storage: H2 files under ./data/vectordb*
- ANN / vector index: jelmerk HNSW (HnswAnnService)
- LLM client: Ollama via local `ollama` CLI (OllamaClient)
- Packaging: Spring Boot fat jar (target/codetools-*.jar)
- Testing: JUnit 5
- Dev OS / shell: Windows (PowerShell)

Keep this file minimal — only stack/runtime/build items that help developers get started.

```
Read NOTES.md before taking any action.

