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

package org.apache.jena.fuseki.access;

import org.apache.jena.assembler.BuildContext;
import org.apache.jena.assembler.Constructor;
import org.apache.jena.assembler.exceptions.AssemblerException;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.assembler.AssemblerUtils;
import org.apache.jena.sparql.util.graph.GraphUtils;
import org.apache.jena.system.G;

/**
 * {@link Constructor} for {@code access:AccessControlledDataset}.
 * Resolves both the base dataset and the security registry through the
 * {@link BuildContext}, so shared datasets are properly deduplicated.
 */
public class AccessDatasetConstructor implements Constructor<DatasetGraph> {

    @Override
    public DatasetGraph newItem(BuildContext cxt, Graph graph, Node node) {
        Resource root = ModelFactory.createModelForGraph(graph).wrapAsResource(node);
        if ( !GraphUtils.exactlyOneProperty(root, VocabSecurity.pSecurityRegistry) )
            throw new AssemblerException(root, "Expected exactly one access:registry property");
        if ( !GraphUtils.exactlyOneProperty(root, VocabSecurity.pDataset) )
            throw new AssemblerException(root, "Expected exactly one access:dataset property");

        Node registryNode = G.getSP(graph, node, VocabSecurity.pSecurityRegistry.asNode());
        Node datasetNode = G.getSP(graph, node, VocabSecurity.pDataset.asNode());

        AuthorizationService sr = cxt.build(graph, registryNode);
        DatasetGraph dsgBase = AssemblerUtils.buildDatasetGraph(cxt, graph, datasetNode);

        return new DatasetGraphAccessControl(dsgBase, sr);
    }
}
