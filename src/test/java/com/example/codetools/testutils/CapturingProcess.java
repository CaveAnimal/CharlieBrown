package com.example.codetools.testutils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

public class CapturingProcess extends Process {
    private final ByteArrayOutputStream stdin = new ByteArrayOutputStream();
    private final ByteArrayInputStream stdout;
    private final ByteArrayInputStream stderr;
    private final int exitCode;

    public CapturingProcess(byte[] stdoutBytes, byte[] stderrBytes, int exitCode) {
        this.stdout = new ByteArrayInputStream(stdoutBytes == null ? new byte[0] : stdoutBytes);
        this.stderr = new ByteArrayInputStream(stderrBytes == null ? new byte[0] : stderrBytes);
        this.exitCode = exitCode;
    }

    @Override
    public OutputStream getOutputStream() {
        return new OutputStream() {
            @Override
            public void write(int b) {
                stdin.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) {
                stdin.write(b, off, len);
            }
        };
    }

    @Override
    public InputStream getInputStream() {
        return stdout;
    }

    @Override
    public InputStream getErrorStream() {
        return stderr;
    }

    @Override
    public int waitFor() {
        return exitCode;
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) {
        return true;
    }

    @Override
    public int exitValue() {
        return exitCode;
    }

    @Override
    public void destroy() {
        // no-op
    }

    @Override
    public Process destroyForcibly() {
        return this;
    }

    @Override
    public boolean isAlive() {
        return false;
    }

    public byte[] getCapturedStdin() {
        return stdin.toByteArray();
    }
}
