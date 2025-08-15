package com.example.codetools;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class DefaultProcessRunner implements ProcessRunner {
    @Override
    public Process start(List<String> command) throws IOException {
        return new ProcessBuilder(command).start();
    }
}
