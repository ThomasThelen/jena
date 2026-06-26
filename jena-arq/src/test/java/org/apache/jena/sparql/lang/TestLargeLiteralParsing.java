/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.jena.sparql.lang;

import org.apache.jena.query.QueryFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Benchmarks SPARQL parsing with large string literals to demonstrate
 * the impact of exponential vs linear buffer growth in the JavaCC CharStream.
 *
 * Run with:
 *   mvn test -pl jena-arq -Dtest=TestLargeLiteralParsing -Drat.skip=true -Xmx6g
 *
 * For the 1 GB case the JVM needs at least ~6 GB heap (the query string itself
 * is ~2 GB in UTF-16, plus the CharStream buffer).  Pass
 *   -Dbenchmark.max_mb=100   to cap at 100 MB if heap is limited.
 */
public class TestLargeLiteralParsing {

    // Each entry: {chars in literal}.
    // 1 char = 1 byte in ASCII content, ~2 bytes in the Java String (UTF-16).
    private static final long[] SIZES_CHARS = {
        1_000L,
        10_000L,
        50_000L,
        100_000L,
        500_000L,
        1_000_000L,      //   ~1 MB
        5_000_000L,      //   ~5 MB
        10_000_000L,     //  ~10 MB
        50_000_000L,     //  ~50 MB
        100_000_000L,    // ~100 MB
    };

    /** Per-run timeout in seconds.  Exceeded → recorded as TIMEOUT. */
    private static final long TIMEOUT_SECONDS = 600;

    private static final int WARMUP_RUNS = 2;
    private static final int TIMED_RUNS  = 3;

    /** Honour -Dbenchmark.max_mb to skip sizes that exceed available heap. */
    private static long maxChars() {
        String prop = System.getProperty("benchmark.max_mb");
        if (prop != null) {
            return Long.parseLong(prop.trim()) * 1_000_000L;
        }
        // Default: use up to 60 % of max heap, leaving room for the buffer copies.
        long heapMb = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        return (long)(heapMb * 0.6) * 1_000_000L / 2; // /2 because UTF-16
    }

    @Test
    public void benchmarkLargeLiterals() throws Exception {
        long ceiling = maxChars();
        System.out.println("\n=== Large Literal Parsing Benchmark ===");
        System.out.printf("(timeout per run: %ds, heap ceiling: ~%.0f MB of literal)%n",
                TIMEOUT_SECONDS, ceiling / 1e6);
        System.out.printf("%-18s %-12s %-16s%n", "Size (chars)", "Size", "Avg time (ms)");
        System.out.println("-".repeat(48));

        List<String[]> csvRows = new ArrayList<>();
        ExecutorService exec = Executors.newSingleThreadExecutor();

        for (long size : SIZES_CHARS) {
            if (size > ceiling) {
                System.out.printf("%-18s %-12s %-16s%n",
                        size, formatSize(size), "SKIPPED (heap)");
                continue;
            }

            String label = formatSize(size);
            String query  = buildQuery(size);

            // Warmup (also time-limited so we detect old-code hangs early)
            boolean timedOut = false;
            for (int i = 0; i < WARMUP_RUNS && !timedOut; i++) {
                Future<?> f = exec.submit(() -> QueryFactory.create(query));
                try { f.get(TIMEOUT_SECONDS, TimeUnit.SECONDS); }
                catch (TimeoutException e) { f.cancel(true); timedOut = true; }
            }

            if (timedOut) {
                System.out.printf("%-18s %-12s %-16s%n", size, label, "TIMEOUT (warmup)");
                csvRows.add(new String[]{String.valueOf(size), "-1"});
                continue;
            }

            // Timed runs
            long total = 0;
            for (int i = 0; i < TIMED_RUNS && !timedOut; i++) {
                final long[] elapsed = {-1};
                Future<?> f = exec.submit(() -> {
                    long t = System.currentTimeMillis();
                    QueryFactory.create(query);
                    elapsed[0] = System.currentTimeMillis() - t;
                });
                try {
                    f.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    total += elapsed[0];
                } catch (TimeoutException e) {
                    f.cancel(true);
                    timedOut = true;
                }
            }

            if (timedOut) {
                System.out.printf("%-18s %-12s %-16s%n", size, label, "TIMEOUT");
                csvRows.add(new String[]{String.valueOf(size), "-1"});
            } else {
                long avg = total / TIMED_RUNS;
                System.out.printf("%-18s %-12s %-16d%n", size, label, avg);
                csvRows.add(new String[]{String.valueOf(size), String.valueOf(avg)});
            }
        }

        exec.shutdownNow();

        System.out.println("\n# CSV for plotting:");
        System.out.println("size_chars,time_ms");
        for (String[] row : csvRows) {
            System.out.println(row[0] + "," + row[1]);
        }
    }

    private static String buildQuery(long literalChars) {
        // Triple-quoted literal so the parser exercises STRING_LITERAL_LONG.
        // Pure ASCII, no escapes — isolates the buffer-expansion cost.
        String chunk = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_-";
        int chunkLen = chunk.length();
        StringBuilder sb = new StringBuilder((int)Math.min(literalChars + 20, Integer.MAX_VALUE));
        sb.append("SELECT * WHERE { ?s ?p \"\"\"");
        for (long i = 0; i < literalChars; i++) {
            sb.append(chunk.charAt((int)(i % chunkLen)));
        }
        sb.append("\"\"\" }");
        return sb.toString();
    }

    private static String formatSize(long chars) {
        if (chars >= 1_000_000_000) return (chars / 1_000_000_000) + " GB";
        if (chars >= 1_000_000)     return (chars / 1_000_000)     + " MB";
        if (chars >= 1_000)         return (chars / 1_000)         + " KB";
        return chars + " B";
    }
}
