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

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;

/**
 * Builds a Java object from an RDF description given as a {@link Graph} and a
 * subject {@link Node}, with a {@link BuildContext} that caches objects
 * within a single build run.
 * <p>
 * The default {@link #construct} implementation handles "same URI → same object"
 * automatically via {@link BuildContext#computeIfAbsent}. Implementors only
 * need to override {@link #newItem}, which is called at most once per distinct
 * node within a build run.
 * <p>
 * This interface operates at the primitive {@code Graph}/{@code Node} layer
 * rather than the Model/{@code Resource} layer.
 *
 * @param <X> the type of object this constructor produces
 * @see ConstructorGroup
 * @see BuildContext
 */
public interface Constructor<X> {

    /**
     * Return the object for {@code node}, using the build-scoped cache in
     * {@code cxt} so that the same URI node always produces the same object
     * within one build run.
     */
    default X construct(BuildContext cxt, Graph graph, Node node) {
        return cxt.computeIfAbsent(node, () -> newItem(cxt, graph, node));
    }

    /**
     * Create a fresh object from the RDF description of {@code node} in
     * {@code graph}. Called at most once per distinct node within a build run.
     */
    X newItem(BuildContext cxt, Graph graph, Node node);
}
