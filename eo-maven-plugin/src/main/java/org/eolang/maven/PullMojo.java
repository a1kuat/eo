/*
 * SPDX-FileCopyrightText: Copyright (c) 2016-2025 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.maven;

import com.jcabi.log.Logger;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Supplier;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.cactoos.text.TextOf;

/**
 * Pull EO files from Objectionary.
 * @since 0.1
 */
@Mojo(
    name = "pull",
    defaultPhase = LifecyclePhase.PROCESS_SOURCES,
    threadSafe = true
)
public final class PullMojo extends SafeMojo {
    /**
     * The directory where to process to.
     */
    static final String DIR = "4-pull";

    /**
     * Cache directory.
     */
    static final String CACHE = "pulled";

    /**
     * The Git hash to pull objects from, in objectionary.
     * If not set, will be computed from {@code tag} field.
     * @since 0.29.6
     */
    @SuppressWarnings("PMD.ImmutableField")
    private CommitHash hash = new ChCached(
        new ChRemote(this.tag)
    );

    /**
     * Objectionary.
     * @since 0.50
     * @checkstyle MemberNameCheck (5 lines)
     */
    @SuppressWarnings("PMD.ImmutableField")
    private Objectionary objectionary = new OyIndexed(
        new OyRemote(this.hash)
    );

    @Override
    public void exec() throws IOException {
        if (this.offline) {
            Logger.info(
                this,
                "No programs were pulled because eo.offline flag is set to TRUE"
            );
        } else {
            this.pull();
        }
    }

    /**
     * Pull them all.
     * @throws IOException If fails
     */
    @SuppressWarnings("PMD.PrematureDeclaration")
    private void pull() throws IOException {
        final long start = System.currentTimeMillis();
        final Collection<TjForeign> tojos = this.scopedTojos().withoutSources();
        final Collection<String> names = new ArrayList<>(0);
        final Path base = this.targetDir.toPath().resolve(PullMojo.DIR);
        final String hsh = this.hash.value();
        for (final TjForeign tojo : tojos) {
            final String object = tojo.identifier();
            try {
                tojo.withSource(this.pulled(object, base, hsh))
                    .withHash(new ChNarrow(this.hash));
            } catch (final IOException exception) {
                throw new IOException(
                    String.format(
                        "Failed to pull '%s' earlier discovered at %s",
                        tojo.identifier(),
                        tojo.discoveredAt()
                    ),
                    exception
                );
            }
            names.add(object);
        }
        if (tojos.isEmpty()) {
            Logger.info(
                this,
                "No programs were pulled in %[ms]s",
                System.currentTimeMillis() - start
            );
        } else {
            Logger.info(
                this,
                "%d program(s) were pulled in %[ms]s: %s",
                tojos.size(),
                System.currentTimeMillis() - start,
                names
            );
        }
    }

    /**
     * Pull one object.
     * @param object Name of the object
     * @param base Base cache path
     * @param hsh Git hash
     * @return The path of .eo file
     * @throws IOException If fails
     */
    private Path pulled(final String object, final Path base, final String hsh) throws IOException {
        final String semver = this.plugin.getVersion();
        final Path target = new Place(object).make(base, AssembleMojo.EO);
        final Supplier<Path> che = new CachePath(
            this.cache.toPath().resolve(PullMojo.CACHE),
            semver,
            hsh,
            base.relativize(target)
        );
        final Footprint generated = new FpGenerated(
            src -> {
                Logger.debug(
                    this,
                    "Pulling %s object from remote objectionary with hash %s",
                    object, hsh
                );
                return new TextOf(this.objectionary.get(object)).asString();
            }
        );
        final Footprint both = new FpUpdateBoth(generated, che);
        return new FpIfReleased(
            this.plugin.getVersion(),
            hsh,
            new FpFork(
                (src, tgt) -> {
                    if (this.overWrite) {
                        Logger.debug(
                            this,
                            "Pulling sources again because \"eo.overWrite=TRUE\""
                        );
                    }
                    return this.overWrite;
                },
                both,
                new FpIfTargetExists(
                    new FpIgnore(),
                    new FpIfTargetExists(
                        tgt -> che.get(),
                        new FpUpdateFromCache(che),
                        both
                    )
                )
            ),
            generated
        ).apply(Paths.get(""), target);
    }
}
