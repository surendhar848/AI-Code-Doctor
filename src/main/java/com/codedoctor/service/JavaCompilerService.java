package com.codedoctor.service;

import com.codedoctor.config.AppProperties;
import com.codedoctor.dto.CompilerDiagnostic;
import com.codedoctor.dto.CompilerResult;
import com.codedoctor.dto.SourceFile;
import com.codedoctor.exception.CompilerUnavailableException;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Compiles submitted source with the JDK's own compiler (javax.tools) and returns real diagnostics.
 *
 * <p>Safety: the code is only COMPILED, never executed; annotation processing is disabled; the classpath
 * is restricted to an empty temp directory; every call uses its own temp directory that is deleted
 * afterwards; and a timeout bounds the wait. javac itself is still a parser for untrusted input, so for
 * public deployment run this application in a locked-down container (see docker/).
 */
@Service
public class JavaCompilerService {

    private static final Logger log = LoggerFactory.getLogger(JavaCompilerService.class);

    private final AppProperties props;
    private final ExecutorService executor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "javac-worker");
        t.setDaemon(true);
        return t;
    });

    public JavaCompilerService(AppProperties props) {
        this.props = props;
    }

    public CompilerResult compile(List<SourceFile> files) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new CompilerUnavailableException(
                    "No Java compiler found. Run the application on a JDK (not a JRE).");
        }
        long start = System.nanoTime();
        Future<CompilerResult> future = executor.submit(() -> doCompile(compiler, files, start));
        try {
            return future.get(props.compileTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Compilation timed out after {}", props.compileTimeout());
            return new CompilerResult(false, true, millisSince(start), List.of(new CompilerDiagnostic(
                    "", 0, 0, "ERROR", "Compilation timed out after " + props.compileTimeout().toSeconds() + " seconds.", "")));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompilerUnavailableException("Compilation was interrupted.", e);
        } catch (ExecutionException e) {
            throw new CompilerUnavailableException("The compiler failed unexpectedly: " + e.getCause(), e.getCause());
        }
    }

    private CompilerResult doCompile(JavaCompiler compiler, List<SourceFile> files, long start) throws IOException {
        Path root = Files.createTempDirectory("codedoctor-");
        try {
            Path src = Files.createDirectories(root.resolve("src"));
            Path out = Files.createDirectories(root.resolve("out"));

            List<Path> paths = new ArrayList<>();
            Map<String, List<String>> linesByFile = new HashMap<>();
            for (SourceFile f : files) {
                // names were validated by CodeValidator; resolve + startsWith is a second line of defence
                Path p = src.resolve(f.name()).normalize();
                if (!p.startsWith(src)) {
                    throw new IOException("Illegal file name: " + f.name());
                }
                Files.writeString(p, f.content(), StandardCharsets.UTF_8);
                paths.add(p);
                linesByFile.put(f.name(), f.content().lines().toList());
            }

            DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
            List<String> options = List.of(
                    "-proc:none",                 // never run annotation processors from untrusted input
                    "-encoding", "UTF-8",
                    "--release", "21",
                    "-Xlint:unchecked",
                    "-cp", out.toString(),        // do NOT inherit the application's classpath
                    "-d", out.toString());

            boolean ok;
            try (StandardJavaFileManager fm = compiler.getStandardFileManager(collector, Locale.ENGLISH, StandardCharsets.UTF_8)) {
                Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromPaths(paths);
                ok = compiler.getTask(new StringWriter(), fm, collector, options, null, units).call();
            }

            List<CompilerDiagnostic> diagnostics = new ArrayList<>();
            for (Diagnostic<? extends JavaFileObject> d : collector.getDiagnostics()) {
                String kind = switch (d.getKind()) {
                    case ERROR -> "ERROR";
                    case WARNING, MANDATORY_WARNING -> "WARNING";
                    default -> "NOTE";
                };
                String file = d.getSource() == null ? "" : Path.of(d.getSource().toUri()).getFileName().toString();
                long line = d.getLineNumber() == Diagnostic.NOPOS ? 0 : d.getLineNumber();
                long col = d.getColumnNumber() == Diagnostic.NOPOS ? 0 : d.getColumnNumber();
                String text = "";
                List<String> lines = linesByFile.get(file);
                if (lines != null && line >= 1 && line <= lines.size()) {
                    text = lines.get((int) line - 1);
                }
                diagnostics.add(new CompilerDiagnostic(file, line, col, kind, d.getMessage(Locale.ENGLISH), text));
            }
            boolean hasError = diagnostics.stream().anyMatch(d -> "ERROR".equals(d.kind()));
            return new CompilerResult(ok && !hasError, false, millisSince(start), diagnostics);
        } finally {
            deleteQuietly(root);
        }
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static void deleteQuietly(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            log.debug("Could not fully delete {}", dir, e);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
