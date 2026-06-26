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

package org.apache.jena.tdb1.assembler;

import org.apache.jena.assembler.BuildContext;
import org.apache.jena.assembler.Constructor;
import org.apache.jena.assembler.exceptions.AssemblerException;
import org.apache.jena.atlas.logging.Log;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.assembler.AssemblerUtils;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.util.graph.GraphUtils;
import org.apache.jena.system.G;
import org.apache.jena.tdb1.TDB1;
import org.apache.jena.tdb1.TDB1Factory;
import org.apache.jena.tdb1.base.file.Location;

/**
 * {@link Constructor} for {@code tdb:DatasetTDB} (and the TDB1 aliases).
 * TDB1 sharing is done system-wide by storage location via {@link TDB1Factory}, so
 * the {@link BuildContext} cache is not the primary deduplication mechanism here.
 */
@SuppressWarnings("removal")
public class TDB1DatasetConstructor implements Constructor<DatasetGraph> {

    @Override
    public DatasetGraph newItem(BuildContext cxt, Graph graph, Node node) {
        Resource root = ModelFactory.createModelForGraph(graph).wrapAsResource(node);
        if ( !GraphUtils.exactlyOneProperty(root, VocabTDB1.pLocation) )
            throw new AssemblerException(root, "No location given");

        String dir = GraphUtils.getStringValue(root, VocabTDB1.pLocation);
        Location loc = Location.create(dir);
        DatasetGraph dsg = TDB1Factory.createDatasetGraph(loc);

        Node udg = G.getSP(graph, node, VocabTDB1.pUnionDefaultGraph.asNode());
        if ( udg != null ) {
            NodeValue nv = NodeValue.makeNode(udg);
            if ( nv.isBoolean() )
                dsg.getContext().set(TDB1.symUnionDefaultGraph, nv.getBoolean());
            else
                Log.warn(TDB1DatasetConstructor.class, "Failed to recognize value for union graph setting (ignored): " + udg);
        }

        AssemblerUtils.mergeContext(root, dsg.getContext());
        return dsg;
    }
}
