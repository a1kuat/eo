/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2022 Objectionary.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.eolang.maven;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import org.apache.maven.model.Dependency;

/**
 * Encapsulates all transitive dependencies of an artifact.
 *
 * @since 0.28.11
 */
final class TransitiveDependencies implements Dependencies {

    /**
     * Decorated.
     */
    private final Dependencies dependencies;

    /**
     * Open constructor.
     *
     * @param file File with all transitive dependencies for Dependency
     * @param dependency Dependency
     */
    TransitiveDependencies(final Path file, final Dependency dependency) {
        this(new FilteredDependencies(
            new JsonDependencies(file),
            Arrays.asList(
                new NoRuntimeDependency(),
                new NoSameDependency(dependency),
                new NoTestingDependency()
            )
        ));
    }

    /**
     * The main constructor.
     *
     * @param dependencies Decorated.
     */
    private TransitiveDependencies(final Dependencies dependencies) {
        this.dependencies = dependencies;
    }

    @Override
    public List<Dependency> toList() {
        return this.dependencies.toList();
    }

    /**
     * Filters runtime dependency eo-runtime.
     *
     * @since 0.28.11
     */
    private static final class NoRuntimeDependency implements Predicate<Dependency> {
        @Override
        public boolean test(final Dependency dependency) {
            return !(
                dependency.getGroupId().equals("org.eolang")
                    && dependency.getArtifactId().equals("eo-runtime")
                );
        }
    }

    /**
     * Filters all dependencies with the same group and artifact id.
     *
     * @since 0.28.11
     */
    private static final class NoSameDependency implements Predicate<Dependency> {

        /**
         * Dependency to check.
         */
        private final Dependency current;

        /**
         * The main constructor.
         *
         * @param current Dependency to check
         */
        private NoSameDependency(final Dependency current) {
            this.current = current;
        }

        @Override
        public boolean test(final Dependency dependency) {
            return !(
                dependency.getGroupId().equals(this.current.getGroupId())
                    && dependency.getArtifactId().equals(this.current.getArtifactId())
                );
        }
    }

    /**
     * Filters all test dependencies.
     *
     * @since 0.28.11
     */
    @SuppressWarnings("PMD.JUnit4TestShouldUseTestAnnotation")
    private static final class NoTestingDependency implements Predicate<Dependency> {
        @Override
        public boolean test(final Dependency dependency) {
            return !dependency.getScope().contains("test");
        }
    }
}
