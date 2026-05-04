package com.feel.gems.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;


public class TelemetryParsingTest {

    private static final Pattern PERF_PATTERN = Pattern.compile("avg_mspt=([0-9]+(?:\\.[0-9]+)?)\\s+p95_mspt=([0-9]+(?:\\.[0-9]+)?)");

    @Test
    void parsesPerfSnapshotLikePerfWorkflowOutput() {
        String sample = "perf snapshot: avg_mspt=14.2 p95_mspt=88.6 players=20 dims=3";
        Matcher m = PERF_PATTERN.matcher(sample);
        assertTrue(m.find(), "Pattern should match perf snapshot output");
        assertEquals("14.2", m.group(1));
        assertEquals("88.6", m.group(2));
    }

    @Test
    void toleratesIntegerValuesToo() {
        String sample = "avg_mspt=12 p95_mspt=34";
        Matcher m = PERF_PATTERN.matcher(sample);
        assertTrue(m.find());
        assertEquals("12", m.group(1));
        assertEquals("34", m.group(2));
    }
}
