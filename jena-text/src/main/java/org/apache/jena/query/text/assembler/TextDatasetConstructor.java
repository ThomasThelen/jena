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

package org.apache.jena.query.text.assembler;

import static org.apache.jena.query.text.assembler.TextVocab.pDataset;
import static org.apache.jena.query.text.assembler.TextVocab.pIndex;
import static org.apache.jena.query.text.assembler.TextVocab.pTextDocProducer;

import org.apache.jena.assembler.BuildContext;
import org.apache.jena.assembler.Constructor;
import org.apache.jena.assembler.exceptions.AssemblerException;
import org.apache.jena.atlas.logging.Log;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.query.text.TextDatasetFactory;
import org.apache.jena.query.text.TextDocProducer;
import org.apache.jena.query.text.TextIndex;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.ARQConstants;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.assembler.AssemblerUtils;
import org.apache.jena.sparql.util.ClsLoader;
import org.apache.jena.system.G;

/**
 * {@link Constructor} for {@code text:TextDataset}.
 * Resolves the base dataset via {@link BuildContext} for deduplication, then
 * wraps it with a text index assembled via the legacy path.
 */
public class TextDatasetConstructor implements Constructor<DatasetGraph> {

    @Override
    public DatasetGraph newItem(BuildContext cxt, Graph graph, Node node) {
        Node datasetNode = G.getSP(graph, node, pDataset.asNode());
        if ( datasetNode == null )
            throw new AssemblerException(null, "Missing text:dataset on text dataset " + node);
        Node indexNode = G.getSP(graph, node, pIndex.asNode());
        if ( indexNode == null )
            throw new AssemblerException(null, "Missing text:index on text dataset " + node);

        DatasetGraph base = AssemblerUtils.buildDatasetGraph(cxt, graph, datasetNode);
        TextIndex textIndex = cxt.build(graph, indexNode);

        TextDocProducer textDocProducer = null;
        Node producerNode = G.getSP(graph, node, pTextDocProducer.asNode());
        if ( producerNode != null && producerNode.isURI() ) {
            Class<?> c = ClsLoader.loadClass(producerNode.getURI(), TextDocProducer.class);
            String className = producerNode.getURI().substring(ARQConstants.javaClassURIScheme.length());
            java.lang.reflect.Constructor<?> dyadic = getConstructor(c, DatasetGraph.class, TextIndex.class);
            java.lang.reflect.Constructor<?> monadic = getConstructor(c, TextIndex.class);
            try {
                if ( dyadic != null )
                    textDocProducer = (TextDocProducer)dyadic.newInstance(base, textIndex);
                else if ( monadic != null )
                    textDocProducer = (TextDocProducer)monadic.newInstance(textIndex);
                else
                    Log.warn(TextDatasetConstructor.class, "No suitable constructor for TextDocProducer '" + className + "'");
            } catch (Exception ex) {
                Log.warn(TextDatasetConstructor.class, "Exception instantiating TextDocProducer '" + className + "': " + ex.getMessage());
            }
        }

        DatasetGraph dsg = TextDatasetFactory.create(base, textIndex, true, textDocProducer);
        Resource root = ModelFactory.createModelForGraph(graph).wrapAsResource(node);
        AssemblerUtils.mergeContext(root, dsg.getContext());
        return dsg;
    }

    private static java.lang.reflect.Constructor<?> getConstructor(Class<?> c, Class<?>... types) {
        try {
            return c.getConstructor(types);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
