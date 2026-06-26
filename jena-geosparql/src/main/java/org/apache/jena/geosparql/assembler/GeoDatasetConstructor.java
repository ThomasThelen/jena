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

package org.apache.jena.geosparql.assembler;

import static org.apache.jena.geosparql.assembler.VocabGeoSPARQL.pApplyDefaultGeometry;
import static org.apache.jena.geosparql.assembler.VocabGeoSPARQL.pDataset;
import static org.apache.jena.geosparql.assembler.VocabGeoSPARQL.pIndexEnabled;
import static org.apache.jena.geosparql.assembler.VocabGeoSPARQL.pIndexExpiries;
import static org.apache.jena.geosparql.assembler.VocabGeoSPARQL.pIndexSizes;
import static org.apache.jena.geosparql.assembler.VocabGeoSPARQL.pInference;
import static org.apache.jena.geosparql.assembler.VocabGeoSPARQL.pQueryRewrite;
import static org.apache.jena.geosparql.assembler.VocabGeoSPARQL.pSpatialIndexFile;
import static org.apache.jena.geosparql.assembler.VocabGeoSPARQL.pSrsUri;
import static org.apache.jena.sparql.util.graph.GraphUtils.getBooleanValue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.jena.assembler.BuildContext;
import org.apache.jena.assembler.Constructor;
import org.apache.jena.assembler.exceptions.AssemblerException;
import org.apache.jena.atlas.io.IO;
import org.apache.jena.geosparql.configuration.GeoSPARQLConfig;
import org.apache.jena.geosparql.configuration.GeoSPARQLOperations;
import org.apache.jena.geosparql.configuration.SrsException;
import org.apache.jena.geosparql.spatial.SpatialIndexConstants;
import org.apache.jena.geosparql.spatial.SpatialIndexException;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.JenaException;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.assembler.AssemblerUtils;
import org.apache.jena.sparql.util.graph.GraphUtils;
import org.apache.jena.system.G;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link Constructor} for {@code geo:GeosparqlDataset} (and the lowercase alias).
 * Resolves the base dataset via {@link BuildContext} for deduplication, then
 * applies the GeoSPARQL spatial index and inference settings.
 */
public class GeoDatasetConstructor implements Constructor<DatasetGraph> {

    private static final Logger LOG = LoggerFactory.getLogger(GeoDatasetConstructor.class);

    @Override
    public DatasetGraph newItem(BuildContext cxt, Graph graph, Node node) {
        Node baseNode = G.getSP(graph, node, pDataset.asNode());
        if ( baseNode == null )
            throw new AssemblerException(null, "Missing geo:dataset on GeoSPARQL dataset " + node);
        DatasetGraph base = AssemblerUtils.buildDatasetGraph(cxt, graph, baseNode);

        Resource root = ModelFactory.createModelForGraph(graph).wrapAsResource(node);

        boolean inference = true;
        if ( root.hasProperty(pInference) )
            inference = getBooleanValue(root, pInference);

        boolean applyDefaultGeometry = false;
        if ( root.hasProperty(pApplyDefaultGeometry) )
            applyDefaultGeometry = getBooleanValue(root, pApplyDefaultGeometry);

        boolean queryRewrite = true;
        if ( root.hasProperty(pQueryRewrite) )
            queryRewrite = getBooleanValue(root, pQueryRewrite);

        boolean indexEnabled = true;
        if ( root.hasProperty(pIndexEnabled) )
            indexEnabled = getBooleanValue(root, pIndexEnabled);

        List<Integer> indexSizes = Arrays.asList(-1, -1, -1);
        if ( root.hasProperty(pIndexSizes) )
            indexSizes = getListInteger(root, pIndexSizes, 3);

        List<Integer> indexExpiries = Arrays.asList(5000, 5000, 5000);
        if ( root.hasProperty(pIndexExpiries) )
            indexExpiries = getListInteger(root, pIndexSizes, 3);

        String spatialIndexFilename = null;
        if ( root.hasProperty(pSpatialIndexFile) )
            spatialIndexFilename = GraphUtils.getStringValue(root, pSpatialIndexFile);

        if ( spatialIndexFilename == null )
            LOG.warn(root + ": No spatial index file location is specified via " + pSpatialIndexFile + ". Spatial index will not be persisted.");

        String srsURI = null;
        if ( root.hasProperty(pSrsUri) )
            srsURI = GraphUtils.getResourceValue(root, pSrsUri).getURI();

        if ( srsURI == null )
            LOG.warn(root + ": No preferred SRS is configured via " + pSrsUri);

        Dataset dataset = DatasetFactory.wrap(base);
        dataset.getContext().set(SpatialIndexConstants.symSrsUri, srsURI);

        if ( applyDefaultGeometry )
            GeoSPARQLOperations.applyDefaultGeometry(dataset);

        if ( inference )
            GeoSPARQLOperations.applyInferencing(dataset);

        if ( indexEnabled ) {
            GeoSPARQLConfig.setupMemoryIndex(indexSizes.get(0), indexSizes.get(1), indexSizes.get(2),
                    (long)indexExpiries.get(0), (long)indexExpiries.get(1), (long)indexExpiries.get(2),
                    queryRewrite);
        } else {
            GeoSPARQLConfig.setupNoIndex(queryRewrite);
        }

        prepareSpatialExtension(dataset, spatialIndexFilename);
        return base;
    }

    private static List<Integer> getListInteger(Resource r, Property p, int len) {
        String integers = GraphUtils.getStringValue(r, p);
        String[] values = integers.split(",");
        List<Integer> integerList = new ArrayList<>();
        for ( String val : values ) {
            val = val.trim();
            integerList.add(Integer.parseInt(val));
        }
        if ( len >= 0 && integerList.size() != len )
            throw new JenaException("Expected list of exactly " + len + " integers");
        return integerList;
    }

    private static void prepareSpatialExtension(Dataset dataset, String spatialIndex) {
        boolean isEmpty = dataset.calculateRead(dataset::isEmpty);
        if ( isEmpty )
            LOG.warn("Dataset is empty. Constructing an empty spatial index that needs to be updated once data is added.");

        try {
            if ( spatialIndex == null ) {
                GeoSPARQLConfig.setupSpatialIndex(dataset);
                return;
            }
            Path spatialIndexPath = Path.of(spatialIndex);
            if ( !Files.exists(spatialIndexPath) || Files.size(spatialIndexPath) == 0 ) {
                GeoSPARQLConfig.setupSpatialIndex(dataset, spatialIndexPath);
                return;
            }
            GeoSPARQLConfig.setupPrecomputedSpatialIndex(dataset, spatialIndexPath);
        } catch (SrsException ex) {
            if ( !ex.getMessage().startsWith("No SRS found") )
                throw ex;
            LOG.warn(ex.getMessage(), ex);
        } catch (IOException ex) {
            IO.exception(ex);
        } catch (SpatialIndexException ex) {
            String msg = "Failed to create spatial index: " + ex.getMessage();
            LOG.error(msg, ex);
            throw new JenaException(msg, ex);
        }
    }
}
