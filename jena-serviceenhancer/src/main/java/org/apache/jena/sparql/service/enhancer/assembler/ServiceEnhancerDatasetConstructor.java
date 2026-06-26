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

package org.apache.jena.sparql.service.enhancer.assembler;

import java.util.Objects;

import com.google.common.base.Preconditions;

import org.apache.jena.assembler.BuildContext;
import org.apache.jena.assembler.Constructor;
import org.apache.jena.assembler.exceptions.AssemblerException;
import org.apache.jena.atlas.logging.Log;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.query.ARQ;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphWrapper;
import org.apache.jena.sparql.core.assembler.AssemblerUtils;
import org.apache.jena.sparql.service.enhancer.impl.ChainingServiceExecutorBulkCache;
import org.apache.jena.sparql.service.enhancer.impl.ServiceResponseCache;
import org.apache.jena.sparql.service.enhancer.impl.util.GraphUtilsExtra;
import org.apache.jena.sparql.service.enhancer.init.ServiceEnhancerConstants;
import org.apache.jena.sparql.service.enhancer.init.ServiceEnhancerInit;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.sparql.util.Symbol;
import org.apache.jena.sparql.util.graph.GraphUtils;
import org.apache.jena.system.G;

/**
 * {@link Constructor} for {@code se:DatasetServiceEnhancer}.
 * Resolves the base dataset via {@link BuildContext} for deduplication, then
 * applies the service-enhancer caching and optimizer settings to its context.
 */
public class ServiceEnhancerDatasetConstructor implements Constructor<DatasetGraph> {

    @Override
    public DatasetGraph newItem(BuildContext cxt, Graph graph, Node node) {
        Resource root = ModelFactory.createModelForGraph(graph).wrapAsResource(node);

        Resource baseDatasetRes = GraphUtils.getResourceValue(root, ServiceEnhancerVocab.baseDataset);
        Objects.requireNonNull(baseDatasetRes, "No ja:baseDataset specified on " + root);

        Node baseNode = G.getSP(graph, node, ServiceEnhancerVocab.baseDataset.asNode());
        DatasetGraph result = AssemblerUtils.buildDatasetGraph(cxt, graph, baseNode);
        Context ctx = result.getContext();
        ServiceEnhancerInit.wrapOptimizer(ctx, ARQ.getContext());

        RDFNode selfIdRes = GraphUtils.getAsRDFNode(root, ServiceEnhancerVocab.datasetId);
        Node selfId = selfIdRes == null
                ? baseDatasetRes.asNode()
                : selfIdRes.asNode();

        RDFNode enableMgmtRdfNode = GraphUtils.getAsRDFNode(root, ServiceEnhancerVocab.enableMgmt);
        boolean enableMgmt = enableMgmtRdfNode != null && enableMgmtRdfNode.asLiteral().getBoolean();

        ctx.set(ServiceEnhancerConstants.datasetId, selfId);

        if ( root.hasProperty(ServiceEnhancerVocab.cacheMaxEntryCount)
                || root.hasProperty(ServiceEnhancerVocab.cachePageSize)
                || root.hasProperty(ServiceEnhancerVocab.cacheMaxPageCount) ) {
            int maxEntryCount = GraphUtilsExtra.getAsInt(root, ServiceEnhancerVocab.cacheMaxEntryCount, ServiceResponseCache.DFT_MAX_ENTRY_COUNT);
            int pageSize = GraphUtilsExtra.getAsInt(root, ServiceEnhancerVocab.cachePageSize, ServiceResponseCache.DFT_PAGE_SIZE);
            int maxPageCount = GraphUtilsExtra.getAsInt(root, ServiceEnhancerVocab.cacheMaxPageCount, ServiceResponseCache.DFT_MAX_PAGE_COUNT);
            Preconditions.checkArgument(maxEntryCount > 0, ServiceEnhancerVocab.cacheMaxEntryCount.getURI() + " requires a value greater than 0");
            Preconditions.checkArgument(pageSize > 0, ServiceEnhancerVocab.cachePageSize.getURI() + " requires a value greater than 0");
            Preconditions.checkArgument(maxPageCount > 0, ServiceEnhancerVocab.cacheMaxPageCount.getURI() + " requires a value greater than 0");
            ServiceResponseCache cache = new ServiceResponseCache(maxEntryCount, pageSize, maxPageCount);
            ServiceResponseCache.set(ctx, cache);
        }

        configureCxt(root, ServiceEnhancerVocab.bulkMaxSize, ctx, ServiceEnhancerConstants.serviceBulkMaxBindingCount,
                false, ChainingServiceExecutorBulkCache.DFT_MAX_BULK_SIZE, GraphUtilsExtra::getAsInt);
        configureCxt(root, ServiceEnhancerVocab.bulkSize, ctx, ServiceEnhancerConstants.serviceBulkBindingCount,
                false, ChainingServiceExecutorBulkCache.DFT_BULK_SIZE, GraphUtilsExtra::getAsInt);
        configureCxt(root, ServiceEnhancerVocab.bulkMaxOutOfBandSize, ctx, ServiceEnhancerConstants.serviceBulkMaxOutOfBandBindingCount,
                false, ChainingServiceExecutorBulkCache.DFT_MAX_OUT_OUF_BAND_SIZE, GraphUtilsExtra::getAsInt);

        if ( enableMgmt ) {
            ctx = ctx.copy();
            ctx.set(ServiceEnhancerConstants.enableMgmt, true);
            result = new DatasetGraphWrapper(result, ctx);
        }

        Log.info(ServiceEnhancerDatasetConstructor.class, "Dataset self id set to " + selfId);
        return result;
    }

    @FunctionalInterface
    private interface TriFunction<A, B, C, R> {
        R apply(A a, B b, C c);
    }

    private static <T> void configureCxt(Resource root, org.apache.jena.rdf.model.Property property,
            Context cxt, Symbol symbol, boolean applyDefault, T defaultValue,
            TriFunction<Resource, org.apache.jena.rdf.model.Property, T, T> getValue) {
        if ( root.hasProperty(property) || applyDefault ) {
            Object value = getValue.apply(root, property, defaultValue);
            cxt.set(symbol, value);
        }
    }
}
