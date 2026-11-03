package com.codedoctor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codedoctor.config.AppProperties;
import com.codedoctor.dto.SourceFile;
import com.codedoctor.exception.InvalidSourceException;
import com.codedoctor.util.CodeValidator;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class CodeValidatorTest {

    private final CodeValidator validator = new CodeValidator(new AppProperties(200, 2, Duration.ofSeconds(5), 3));

    @Test
    void emptyInputIsRejected() {
        assertThrows(InvalidSourceException.class, () -> validator.normalize(List.of()));
        assertThrows(InvalidSourceException.class, () -> validator.normalize(List.of(new SourceFile("A.java", "   "))));
    }

    @Test
    void oversizedSourceIsRejected() {
        assertThrows(InvalidSourceException.class,
                () -> validator.normalize(List.of(new SourceFile("A.java", "x".repeat(201)))));
    }

    @Test
    void tooManyFilesAreRejected() {
        List<SourceFile> three = List.of(new SourceFile("A.java", "class A{}"), new SourceFile("B.java", "class B{}"), new SourceFile("C.java", "class C{}"));
        assertThrows(InvalidSourceException.class, () -> validator.normalize(three));
    }

    @Test
    void pathTraversalNamesAreRejected() {
        List<SourceFile> files = List.of(new SourceFile("../Evil.java", "class Evil{}"), new SourceFile("B.java", "class B{}"));
        assertThrows(InvalidSourceException.class, () -> validator.normalize(files));
    }

    @Test
    void singleFileNameIsInferredFromPublicClass() {
        List<SourceFile> out = validator.normalize(List.of(new SourceFile("Untitled.java", "public class Calculator {}")));
        assertEquals("Calculator.java", out.get(0).name());
    }

    @Test
    void correctedFilesAreMergedOverOriginals() {
        List<SourceFile> originals = List.of(new SourceFile("A.java", "class A{}"), new SourceFile("B.java", "class B{}"));
        List<SourceFile> merged = validator.mergeCorrected(originals, List.of(new SourceFile("A.java", "```java\nclass A{int x;}\n```")));
        assertEquals(2, merged.size());
        assertEquals("class A{int x;}", merged.get(0).content());
        assertEquals("class B{}", merged.get(1).content());
    }
}
