package com.example.codetools;

import java.io.IOException;
import java.util.List;

public interface ProcessRunner {
    Process start(List<String> command) throws IOException;
}
