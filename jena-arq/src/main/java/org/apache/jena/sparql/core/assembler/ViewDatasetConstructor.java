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

package org.apache.jena.sparql.core.assembler;

import org.apache.jena.assembler.BuildContext;
import org.apache.jena.assembler.Constructor;
import org.apache.jena.assembler.exceptions.AssemblerException;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphWrapper;
import org.apache.jena.system.G;

/**
 * {@link Constructor} for {@code ja:ViewDataset}.
 * Wraps a base dataset (given by {@code ja:dataset}) in a {@link DatasetGraphWrapper}
 * so that extra context settings can be layered on top.
 * The base dataset is resolved through the {@link BuildContext} cache, so multiple
 * views of the same URI share one underlying dataset instance.
 */
public class ViewDatasetConstructor implements Constructor<DatasetGraph> {

    @Override
    public DatasetGraph newItem(BuildContext cxt, Graph graph, Node node) {
        Node baseNode = G.getSP(graph, node, DatasetAssemblerVocab.pDataset.asNode());
        if ( baseNode == null )
            throw new AssemblerException(null, "Missing ja:dataset on view dataset " + node);
        DatasetGraph base = AssemblerUtils.buildDatasetGraph(cxt, graph, baseNode);
        DatasetGraph dsg = new DatasetGraphWrapper(base);
        Resource root = ModelFactory.createModelForGraph(graph).wrapAsResource(node);
        AssemblerUtils.mergeContext(root, dsg.getContext());
        return dsg;
    }
}
