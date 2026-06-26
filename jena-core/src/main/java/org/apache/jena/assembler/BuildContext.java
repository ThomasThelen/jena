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
 *
 *   SPDX-License-Identifier: Apache-2.0
 */

package org.apache.jena.assembler;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;

/**
 * A build-scoped cache for assembler objects, keyed by RDF {@link Node}.
 * <p>
 * Passed explicitly through the new {@link Constructor}/{@link ConstructorGroup}
 * API so that "same URI → same object" is guaranteed within a single assembly
 * run without any global or thread-local state in the primary call path.
 * <p>
 * A thread-local bridge ({@link #current()}) exists only to support legacy
 * {@link Assembler} implementations that cannot receive a {@code BuildContext}
 * parameter. It is set exclusively by {@link ConstructorGroup} when falling
 * back to the legacy path.
 * <p>
 * Typical usage (e.g., in Fuseki):
 * <pre>
 *   BuildContext cxt = BuildContext.create();
 *   DatasetGraph dsg = cxt.build(configGraph, datasetNode);
 * </pre>
 *
 * @see Constructor
 * @see ConstructorGroup
 */
public class BuildContext {

    /**
     * Bridge for legacy {@link Assembler} implementations.
     * Set only by {@link ConstructorGroup#legacyFallback} while calling
     * {@link Assembler#general()}; never set by callers of the new API.
     */
    private static final ThreadLocal<BuildContext> BRIDGE = new ThreadLocal<>();

    private final Map<Node, Object> cache = new HashMap<>();
    private final ConstructorGroup constructors;

    private BuildContext(ConstructorGroup constructors) {
        this.constructors = constructors;
    }

    /** Create a new BuildContext backed by {@link ConstructorGroup#global()}. */
    public static BuildContext create() {
        return new BuildContext(ConstructorGroup.global());
    }

    /**
     * Return the {@code BuildContext} bridged to this thread by
     * {@link ConstructorGroup} while a legacy {@link Assembler} call is in
     * progress, or {@code null} if no legacy call is active.
     * <p>
     * Legacy assemblers (e.g., {@code DatasetAssembler}) use this to
     * participate in build-scoped caching even though they cannot receive a
     * {@code BuildContext} parameter directly.
     */
    public static BuildContext current() {
        return BRIDGE.get();
    }

    /** Package-private: read by {@link ConstructorGroup}. */
    static BuildContext getBridge() {
        return BRIDGE.get();
    }

    /** Package-private: set by {@link ConstructorGroup#legacyFallback}. */
    static void setBridge(BuildContext cxt) {
        BRIDGE.set(cxt);
    }

    /**
     * Return the cached object for {@code key}, or invoke {@code builder},
     * cache its result, and return it. The builder is called at most once per
     * key per build run.
     * <p>
     * Uses a manual get-then-put rather than
     * {@link java.util.HashMap#computeIfAbsent} because assembler builders
     * recursively call back into this method for nested resources, and
     * {@code HashMap.computeIfAbsent} throws
     * {@link java.util.ConcurrentModificationException} when the mapping
     * function modifies the same map (even under different keys).
     */
    @SuppressWarnings("unchecked")
    public <T> T computeIfAbsent(Node key, Supplier<T> builder) {
        T existing = (T) cache.get(key);
        if ( existing != null )
            return existing;
        T value = builder.get();
        cache.put(key, value);
        return value;
    }

    /**
     * Build the object described by {@code node} in {@code graph}, using this
     * context's {@link ConstructorGroup} for dispatch and this context's cache
     * for deduplication.
     */
    public <X> X build(Graph graph, Node node) {
        return constructors.construct(this, graph, node);
    }
}
