package com.codedoctor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codedoctor.config.AppProperties;
import com.codedoctor.dto.CompilerDiagnostic;
import com.codedoctor.dto.CompilerResult;
import com.codedoctor.dto.SourceFile;
import com.codedoctor.service.JavaCompilerService;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Needs a JDK (not a JRE) to run - the same requirement the application has. */
class JavaCompilerServiceTest {

    private final JavaCompilerService compiler =
            new JavaCompilerService(new AppProperties(50_000, 10, Duration.ofSeconds(15), 3));

    private CompilerResult compile(String name, String code) {
        return compiler.compile(List.of(new SourceFile(name, code)));
    }

    @Test
    void validCodeCompiles() {
        CompilerResult r = compile("Main.java", "public class Main { public static void main(String[] a) { System.out.println(1); } }");
        assertTrue(r.success());
        assertTrue(r.diagnostics().isEmpty());
    }

    @Test
    void typeMismatchIsReportedWithLineNumber() {
        String code = """
                public class Main {
                    public static void main(String[] args) {
                        int number = "Hello";
                    }
                }
                """;
        CompilerResult r = compile("Main.java", code);
        assertFalse(r.success());
        CompilerDiagnostic d = r.diagnostics().get(0);
        assertEquals("ERROR", d.kind());
        assertEquals(3, d.line());
        assertEquals("Main.java", d.file());
        assertTrue(d.message().contains("incompatible types"));
    }

    @Test
    void missingSemicolonIsDetected() {
        CompilerResult r = compile("Main.java", "public class Main { void f() { int x = 1 } }");
        assertFalse(r.success());
        assertTrue(r.diagnostics().get(0).message().contains("';' expected"));
    }

    @Test
    void multipleFilesCompileTogether() {
        List<SourceFile> files = List.of(
                new SourceFile("Main.java", "public class Main { public static void main(String[] a) { System.out.println(new Helper().id()); } }"),
                new SourceFile("Helper.java", "public class Helper { int id() { return 7; } }"));
        assertTrue(compiler.compile(files).success());
    }

    @Test
    void annotationProcessingAndApplicationClasspathAreNotAvailable() {
        // Spring classes must not be visible to submitted code.
        CompilerResult r = compile("Main.java", "import org.springframework.stereotype.Service; @Service public class Main {}");
        assertFalse(r.success());
    }
}
