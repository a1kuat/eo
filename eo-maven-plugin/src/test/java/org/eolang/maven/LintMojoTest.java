/*
 * SPDX-FileCopyrightText: Copyright (c) 2016-2025 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.maven;

import com.jcabi.xml.XMLDocument;
import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import org.cactoos.io.ResourceOf;
import org.cactoos.text.TextOf;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.io.FileMatchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test cases for {@link LintMojo}.
 *
 * @since 0.31.0
 */
@SuppressWarnings({"PMD.AvoidDuplicateLiterals", "PMD.TooManyMethods"})
@ExtendWith(MktmpResolver.class)
@ExtendWith(RandomProgramResolver.class)
final class LintMojoTest {
    @Test
    void doesNotFailWithNoErrorsAndWarnings(@Mktmp final Path temp) {
        Assertions.assertDoesNotThrow(
            () -> new FakeMaven(temp)
                .withHelloWorld()
                .execute(new FakeMaven.Lint()),
            "Correct program should not have failed, but it does"
        );
    }

    @Test
    void detectsErrorsSuccessfully(@Mktmp final Path temp) throws IOException {
        final FakeMaven maven = new FakeMaven(temp)
            .withProgram(
                "+package f\n",
                "# No comments.",
                "[] > main",
                "  QQ.io.stdout",
                "    \"Hello world\""
            );
        Assertions.assertThrows(
            IllegalStateException.class,
            () -> maven.execute(new FakeMaven.Lint()),
            "Program with noname attributes should have failed or error, but it didn't"
        );
        final Path xmir = maven.result().get("target/5-lint/foo/x/main.xmir");
        MatcherAssert.assertThat(
            "Linted file should exist",
            xmir,
            Matchers.not(Matchers.equalTo(null))
        );
        MatcherAssert.assertThat(
            "Critical error must exist in linted XMIR",
            new XMLDocument(xmir).nodes("/program/errors/error[@severity='error']"),
            Matchers.hasSize(1)
        );
    }

    @Test
    void detectsCriticalErrorsSuccessfully(@Mktmp final Path temp) throws IOException {
        final FakeMaven maven = new FakeMaven(temp)
            .withProgram(
                "+package f\n",
                "# No comments.",
                "[] > main",
                "    \"Hello world\""
            );
        Assertions.assertThrows(
            IllegalStateException.class,
            () -> maven.execute(new FakeMaven.Lint()),
            "Wrong program should have failed or error, but it didn't"
        );
        MatcherAssert.assertThat(
            "Verified file should not exist",
            maven.result().get("target/6-verify/foo/x/main.xmir"),
            Matchers.equalTo(null)
        );
        MatcherAssert.assertThat(
            "Error must exist in shaken XMIR",
            new XMLDocument(
                maven.result().get("target/2-shake/foo/x/main.xmir")
            ).nodes("//errors/error[@severity='critical']"),
            Matchers.hasSize(3)
        );
    }

    @Test
    void detectsWarningWithCorrespondingFlag(@Mktmp final Path temp) throws IOException {
        final FakeMaven maven = new FakeMaven(temp)
            .withProgram(
                "+package f\n",
                "# No comments.",
                "[] > main",
                "  # No comments.",
                "  [] > @",
                "    \"Hello world\" > @"
            )
            .with("failOnWarning", true);
        Assertions.assertThrows(
            IllegalStateException.class,
            () -> maven.execute(new FakeMaven.Lint()),
            "Program with sparse decorated object should have failed on warning, but it didn't"
        );
        MatcherAssert.assertThat(
            "Warning must exist in shaken XMIR",
            new XMLDocument(
                maven.result().get("target/5-lint/foo/x/main.xmir")
            ).nodes("//errors/error[@severity='warning']"),
            Matchers.hasSize(Matchers.greaterThanOrEqualTo(2))
        );
    }

    @Test
    void doesNotDetectWarningWithoutCorrespondingFlag(@Mktmp final Path temp) {
        Assertions.assertDoesNotThrow(
            () -> new FakeMaven(temp)
                .withProgram(
                    "+package f",
                    "+unlint object-has-data\n",
                    "# No comments.",
                    "[] > main",
                    "  # No comments.",
                    "  [] > x",
                    "    \"Hello world\" > @"
                )
                .with("failOnWarning", false)
                .execute(new FakeMaven.Lint()),
            "Program with sparse decorated object should not have failed on warning without flag, but it does"
        );
    }

    @Test
    void failsOptimizationOnError(@Mktmp final Path temp) {
        Assertions.assertThrows(
            IllegalStateException.class,
            () -> new FakeMaven(temp)
                .withProgram(
                    "+package f",
                    "+alias THIS-IS-WRONG org.eolang.io.stdout\n",
                    "# No comments.",
                    "[args] > main",
                    "  (stdout \"Hello!\").print > @"
                )
                .execute(new FakeMaven.Lint()),
            "Error in the eo code because of invalid alias, should fail"
        );
    }

    @Test
    void failsOptimizationOnCritical(@Mktmp final Path temp) {
        Assertions.assertThrows(
            IllegalStateException.class,
            () -> new FakeMaven(temp)
                .withProgram(
                    "+package f\n",
                    "# No comments.",
                    "[args] > main",
                    "  seq > @",
                    "    TRUE > x",
                    "    FALSE > x"
                ).with("trackTransformationSteps", true)
                .execute(new FakeMaven.Lint()),
            "Program should have failed, but it didn't"
        );
    }

    @Test
    void failsParsingOnError(@Mktmp final Path temp) {
        Assertions.assertThrows(
            IllegalStateException.class,
            () -> new FakeMaven(temp)
                .withProgram("something > is wrong here")
                .execute(new FakeMaven.Lint()),
            "Program with invalid syntax should have failed, but it didn't"
        );
    }

    @Test
    void failsOnInvalidProgram(@Mktmp final Path temp) {
        Assertions.assertThrows(
            IllegalStateException.class,
            () -> new FakeMaven(temp)
                .withProgram(
                    "+alias stdout org.eolang.io.stdout",
                    "+home https://github.com/objectionary/eo",
                    "+package test",
                    "+version 0.0.0",
                    "",
                    "[x] < wrong>",
                    "  (stdout \"Hello!\" x).print"
                )
                .execute(new FakeMaven.Lint()),
                "Invalid program with wrong syntax should have failed to assemble, but it didn't"
        );
    }

    @Test
    void failsOnWarning(@Mktmp final Path temp) {
        Assertions.assertThrows(
            IllegalStateException.class,
            () -> new FakeMaven(temp)
                .withProgram(
                    "+architect yegor256@gmail.com",
                    "+tests",
                    "+package org.eolang.examples\n",
                    "# No comments.",
                    "[] > main",
                    "  [] > @",
                    "    hello > test"
                )
                .with("failOnWarning", true)
                .execute(new FakeMaven.Lint()),
            "Program with warning should fail"
        );
    }

    @Test
    void skipsAlreadyLinted(@Mktmp final Path temp) throws IOException {
        final FakeMaven maven = new FakeMaven(temp)
            .withHelloWorld()
            .allTojosWithHash(CommitHash.FAKE)
            .execute(new FakeMaven.Lint());
        final Path path = maven.result().get(
            String.format("target/%s/foo/x/main.%s", LintMojo.DIR, AssembleMojo.XMIR)
        );
        final long mtime = path.toFile().lastModified();
        maven.execute(LintMojo.class);
        MatcherAssert.assertThat(
            "VerifyMojo must skip verification if XMIR was already verified",
            path.toFile().lastModified(),
            Matchers.is(mtime)
        );
    }

    @Test
    void savesVerifiedResultsToCache(@Mktmp final Path temp) throws IOException {
        final Path cache = temp.resolve("cache");
        final String hash = "abcdef1";
        new FakeMaven(temp)
            .withHelloWorld()
            .with("cache", cache.toFile())
            .allTojosWithHash(() -> hash)
            .execute(new FakeMaven.Lint());
        MatcherAssert.assertThat(
            "Verified results must be saved to cache",
            cache.resolve(LintMojo.CACHE)
                .resolve(FakeMaven.pluginVersion())
                .resolve(hash)
                .resolve("foo/x/main.xmir").toFile(),
            FileMatchers.anExistingFile()
        );
    }

    @Test
    void getsAlreadyVerifiedResultsFromCache(@Mktmp final Path temp) throws Exception {
        final TextOf cached = new TextOf(
            new ResourceOf("org/eolang/maven/main.xml")
        );
        final Path cache = temp.resolve("cache");
        final String hash = "abcdef1";
        new Saved(
            cached,
            cache
                .resolve(LintMojo.CACHE)
                .resolve(FakeMaven.pluginVersion())
                .resolve(hash)
                .resolve("foo/x/main.xmir")
        ).value();
        Files.setLastModifiedTime(
            cache.resolve(
                Paths
                    .get(LintMojo.CACHE)
                    .resolve(FakeMaven.pluginVersion())
                    .resolve(hash)
                    .resolve("foo/x/main.xmir")
            ),
            FileTime.fromMillis(System.currentTimeMillis() + 50_000)
        );
        new FakeMaven(temp)
            .withHelloWorld()
            .with("cache", cache.toFile())
            .allTojosWithHash(() -> hash)
            .execute(new FakeMaven.Lint());
        MatcherAssert.assertThat(
            "Cached result should match the original verified XML document",
            new XMLDocument(
                new HmBase(temp).load(
                    Paths.get(
                        String.format(
                            "target/%s/foo/x/main.%s",
                            LintMojo.DIR,
                            AssembleMojo.XMIR
                        )
                    )
                ).asBytes()
            ),
            Matchers.is(new XMLDocument(cached.asString()))
        );
    }
}
