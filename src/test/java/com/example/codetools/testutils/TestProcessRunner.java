package com.example.codetools.testutils;

import com.example.codetools.ProcessRunner;

import java.io.IOException;
import java.util.List;

public class TestProcessRunner implements ProcessRunner {
    private final CapturingProcess process;

    public TestProcessRunner(CapturingProcess process) {
        this.process = process;
    }

    @Override
    public Process start(List<String> command) throws IOException {
        return process;
    }
}
