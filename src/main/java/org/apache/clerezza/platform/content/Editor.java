/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.clerezza.platform.content;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.URL;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Providers;
import org.apache.clerezza.rdf.utils.GraphUtils.NoSuchSubGraphException;

import org.osgi.framework.Bundle;
import org.osgi.service.component.ComponentContext;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.clerezza.jaxrs.utils.TrailingSlash;
import org.apache.clerezza.platform.graphprovider.content.ContentGraphProvider;
import org.apache.clerezza.commons.rdf.ImmutableGraph;
import org.apache.clerezza.commons.rdf.Graph;
import org.apache.clerezza.commons.rdf.IRI;
import org.apache.clerezza.rdf.core.access.TcManager;
import org.apache.clerezza.rdf.core.serializedform.Serializer;
import org.apache.clerezza.rdf.utils.GraphNode;
import org.apache.clerezza.rdf.utils.GraphUtils;
import org.apache.clerezza.web.fileserver.BundlePathNode;
import org.apache.clerezza.web.fileserver.FileServer;
import org.wymiwyg.commons.util.dirbrowser.PathNode;

/**
 * This Jax-rs root resource provides an ajax edito ro edit content structured
 * using the Discobits ontology
 *
 * @author rbn
 *
 */
@Component
@Service(Object.class)
@Property(name = "javax.ws.rs", boolValue = true)
@Path("tools/editor")
public class Editor extends FileServer {

    @Reference
    private ContentGraphProvider cgProvider;

    @Reference
    private TcManager tcManager;

    @Reference
    private Serializer serializer;

    private static final Logger logger = LoggerFactory.getLogger(Editor.class);

    private Providers providers;
    private final String rdfXml = "application/rdf+xml";

    /**
     * On activation the {@link FileServer} is prepared
     *
     * @param context
     */
    protected void activate(ComponentContext context) {
        configure(context.getBundleContext());
    }

    /**
     * The providers are injected by the Jax-rs implementation and used to
     * locate readers for the RDF content of the body
     *
     * @param providers
     */
    @Context
    public void setProviders(Providers providers) {
        this.providers = providers;
    }

    @GET
    @Path("get")
    public GraphNode getDiscobit(@QueryParam("resource") IRI uri,
            @QueryParam("graph") IRI graphUri) {
        final Graph mGraph = graphUri == null ? cgProvider.getContentGraph()
                : tcManager.getGraph(graphUri);
        return new GraphNode(uri, mGraph);
    }

    /**
     * replaces the subgraph serialized with RDF/XML in <code>revokedString
     * </code> with the one from <code>assertedString</code>.
     *
     * @param graphUri the graph within which the replacement has to take place
     * or null for the content graph
     * @param assertedString the asserted ImmutableGraph as RDF/XML
     * @param revokedString the revoked ImmutableGraph as RDF/XML
     */
    @POST
    @Path("post")
    public void postDiscobit(@QueryParam("graph") IRI graphUri,
            @FormParam("assert") String assertedString,
            @FormParam("revoke") String revokedString,
            @FormParam("rdfFormat") String rdfFormat) {
        if (rdfFormat == null) {
            rdfFormat = rdfXml;
        }
        MediaType mediaType = MediaType.valueOf(rdfFormat);
        MessageBodyReader<ImmutableGraph> graphReader = providers.getMessageBodyReader(ImmutableGraph.class, ImmutableGraph.class, null, mediaType);
        final ImmutableGraph assertedGraph;
        final ImmutableGraph revokedGraph;
        try {
            assertedGraph = graphReader.readFrom(ImmutableGraph.class, ImmutableGraph.class, new Annotation[0], mediaType, null, new ByteArrayInputStream(assertedString.getBytes()));
            revokedGraph = graphReader.readFrom(ImmutableGraph.class, ImmutableGraph.class, new Annotation[0], mediaType, null, new ByteArrayInputStream(revokedString.getBytes()));
        } catch (IOException ex) {
            logger.error("reading graph {}", ex);
            throw new WebApplicationException(ex, 500);
        }
        final Graph mGraph = graphUri == null ? cgProvider.getContentGraph()
                : tcManager.getGraph(graphUri);
        try {
            System.out.println("Revoked:");
            logGraph(revokedGraph);
            System.out.println("Asserted:");
            logGraph(assertedGraph);
            GraphUtils.removeSubGraph(mGraph, revokedGraph);
        } catch (NoSuchSubGraphException ex) {
            throw new RuntimeException(ex);
        }
        mGraph.addAll(assertedGraph);
    }

    private void logGraph(Graph graph) {
        serializer.serialize(System.out, graph, "text/turtle");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        serializer.serialize(baos, graph, "text/turtle");
        if (new String(baos.toByteArray()).contains("\r")) {
            System.out.println("The above graph conatins \\r");
        }
        System.out.println();
    }

    @GET
    //this is to work around
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public PathNode getStaticFile(@Context UriInfo uriInfo) {
        TrailingSlash.enforcePresent(uriInfo);
        final PathNode node = getNode("disco.xhtml");
        logger.debug("serving static {}", node);
        return node;
    }

}
