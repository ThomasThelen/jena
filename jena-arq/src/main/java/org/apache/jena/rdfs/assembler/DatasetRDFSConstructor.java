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

package org.apache.jena.rdfs.assembler;

import static org.apache.jena.sparql.util.graph.GraphUtils.getAsStringValue;

import org.apache.jena.assembler.BuildContext;
import org.apache.jena.assembler.Constructor;
import org.apache.jena.assembler.exceptions.AssemblerException;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdfs.RDFSFactory;
import org.apache.jena.rdfs.SetupRDFS;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.assembler.AssemblerUtils;
import org.apache.jena.system.G;

/**
 * {@link Constructor} for {@code ja:DatasetRDFS}.
 * Resolves the base dataset via {@link BuildContext} so that shared base
 * datasets are deduplicated, then wraps with RDFS inference.
 */
public class DatasetRDFSConstructor implements Constructor<DatasetGraph> {

    @Override
    public DatasetGraph newItem(BuildContext cxt, Graph graph, Node node) {
        Node baseNode = G.getSP(graph, node, VocabRDFS.pDataset.asNode());
        if ( baseNode == null )
            throw new AssemblerException(null, "Missing ja:dataset on RDFS dataset " + node);

        DatasetGraph base = AssemblerUtils.buildDatasetGraph(cxt, graph, baseNode);

        Resource root = ModelFactory.createModelForGraph(graph).wrapAsResource(node);
        String schemaFile = getAsStringValue(root, VocabRDFS.pRdfsSchemaFile);
        if ( schemaFile == null )
            throw new AssemblerException(null, "Missing ja:rdfsSchema on RDFS dataset " + node);

        Graph schema = RDFDataMgr.loadGraph(schemaFile);
        SetupRDFS setup = RDFSFactory.setupRDFS(schema);
        DatasetGraph dsg = RDFSFactory.datasetRDFS(base, setup);
        AssemblerUtils.mergeContext(root, dsg.getContext());
        return dsg;
    }
}
