package com.example.codetools;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import com.example.codetools.testutils.CapturingProcess;
import com.example.codetools.testutils.TestProcessRunner;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OllamaClientProcessTest {

    @Test
    public void writesPromptToProcessStdin() throws Exception {
        Environment env = mock(Environment.class);
        when(env.getProperty("ollama.command", "ollama")).thenReturn("ollama");
        when(env.getProperty("ollama.model", "codellama:13b-instruct")).thenReturn("model:x");

        OllamaPromptBuilder builder = new OllamaPromptBuilder();

        CapturingProcess cp = new CapturingProcess("ok\n".getBytes(), new byte[0], 0);
        TestProcessRunner runner = new TestProcessRunner(cp);

        OllamaClient client = new OllamaClient(env, builder, runner);
        List<QueryModels.CodeSnippet> snippets = Collections.emptyList();
        String resp = client.queryModel("hello?", snippets);

        String written = new String(cp.getCapturedStdin(), "UTF-8");
        assertThat(written).contains("Question: hello?");
        assertThat(resp).contains("ok");
    }
}
