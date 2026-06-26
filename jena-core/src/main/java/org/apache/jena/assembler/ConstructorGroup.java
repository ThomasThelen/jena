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

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

/**
 * A registry of {@link Constructor} implementations, dispatching by RDF type.
 * <p>
 * For a given subject node, {@link #construct} looks up the node's
 * {@code rdf:type} against registered constructors. If a match is found the
 * registered {@link Constructor} is used; otherwise the call falls back to
 * the legacy {@link Assembler#general()} path.
 * <p>
 * The legacy fallback sets a thread-local bridge so that legacy
 * {@link Assembler} implementations (which cannot receive a
 * {@link BuildContext} parameter) can still participate in build-scoped
 * caching via {@link BuildContext#current()}.
 *
 * @see Constructor
 * @see BuildContext
 */
public class ConstructorGroup {

    private static final ConstructorGroup GLOBAL = new ConstructorGroup();

    private final Map<Node, Constructor<?>> mappings = new HashMap<>();

    public ConstructorGroup() {}

    /** The process-wide default group, populated during Jena initialisation. */
    public static ConstructorGroup global() {
        return GLOBAL;
    }

    /** Register {@code ctor} as the handler for resources of the given RDF type. */
    public <X> void register(Node type, Constructor<X> ctor) {
        mappings.put(type, ctor);
    }

    /**
     * Build the object described by {@code node} in {@code graph}.
     * <p>
     * Iterates over the {@code rdf:type} values of {@code node} looking for a
     * registered {@link Constructor}. The first match wins. If none match, the
     * call delegates to the legacy {@link Assembler#general()} path with the
     * {@link BuildContext} bridged via a thread-local.
     */
    @SuppressWarnings("unchecked")
    public <X> X construct(BuildContext cxt, Graph graph, Node node) {
        // Direct type lookup — no RDFS inference needed for concrete types.
        var iter = graph.find(node, RDF.type.asNode(), Node.ANY);
        try {
            while ( iter.hasNext() ) {
                Node type = iter.next().getObject();
                Constructor<?> ctor = mappings.get(type);
                if ( ctor != null ) {
                    iter.close();
                    return ((Constructor<X>) ctor).construct(cxt, graph, node);
                }
            }
        } finally {
            iter.close();
        }
        // No registered constructor — fall through to legacy Assembler.general().
        return legacyFallback(cxt, graph, node);
    }

    /**
     * Call the legacy {@link Assembler#general()} path, setting the
     * {@link BuildContext} bridge so that legacy {@link Assembler}
     * implementations can participate in build-scoped caching.
     */
    @SuppressWarnings("unchecked")
    private <X> X legacyFallback(BuildContext cxt, Graph graph, Node node) {
        Resource resource = ModelFactory.createModelForGraph(graph).wrapAsResource(node);
        BuildContext previous = BuildContext.getBridge();
        BuildContext.setBridge(cxt);
        try {
            return (X) Assembler.general().open(resource);
        } finally {
            BuildContext.setBridge(previous);
        }
    }
}
