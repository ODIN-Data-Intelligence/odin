package com.odin.catalog.harvest.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.catalog.harvest.domain.run.HarvestRun;
import com.odin.catalog.harvest.domain.source.HarvestSource;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.DCTerms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Component
public class DcatHttpConnector implements HarvestConnector {

    private static final Logger log = LoggerFactory.getLogger(DcatHttpConnector.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Override
    public String sourceType() {
        return "dcat_http";
    }

    private Model loadRdfModel(String url) {
        Model model = ModelFactory.createDefaultModel();
        String lower = url.toLowerCase();
        if (lower.endsWith(".json") || lower.endsWith(".jsonld")) {
            RDFParser.source(url).lang(Lang.JSONLD).parse(model);
        } else {
            RDFDataMgr.read(model, url);
        }
        return model;
    }

    @Override
    public boolean testConnection(HarvestSource source) {
        try {
            String body = fetchBody(source.baseUrl());
            JsonNode root = MAPPER.readTree(body);
            if (isPodFormat(root)) return root.path("dataset").isArray();
            Model model = ModelFactory.createDefaultModel();
            RDFParser.source(source.baseUrl()).lang(Lang.JSONLD).parse(model);
            return !model.isEmpty();
        } catch (Exception e) {
            log.warn("DCAT HTTP connection test failed for {}: {}", source.baseUrl(), e.getMessage());
            return false;
        }
    }

    @Override
    public Stream<HarvestEntity> harvest(HarvestRun run, HarvestSource source) {
        log.info("Harvesting DCAT from {}", source.baseUrl());
        try {
            String body = fetchBody(source.baseUrl());
            JsonNode root = MAPPER.readTree(body);

            // US POD plain-JSON format (no @context / @type at root)
            if (isPodFormat(root)) {
                return harvestPod(root, source.baseUrl());
            }

            // Proper JSON-LD / Turtle / RDF-XML via Jena
            return harvestRdf(body, source.baseUrl());

        } catch (Exception e) {
            log.error("Harvest failed for {}: {}", source.baseUrl(), e.getMessage(), e);
            return Stream.empty();
        }
    }

    // ── US Project Open Data (POD) plain JSON ──────────────────────────────

    private boolean isPodFormat(JsonNode root) {
        return !root.has("@context") && !root.has("@type") && root.has("dataset");
    }

    private Stream<HarvestEntity> harvestPod(JsonNode root, String baseUrl) {
        List<HarvestEntity> entities = new ArrayList<>();
        root.path("dataset").forEach(ds -> {
            String identifier = ds.path("identifier").asText(null);
            String landingPage = ds.path("landingPage").asText(null);
            String uri = landingPage != null ? landingPage
                : (identifier != null ? identifier : null);
            String title = ds.path("title").asText(null);
            String description = ds.path("description").asText(null);

            List<String> keywords = new ArrayList<>();
            ds.path("keyword").forEach(k -> keywords.add(k.asText()));

            List<String> themes = new ArrayList<>();
            ds.path("theme").forEach(t -> themes.add(t.asText()));

            List<HarvestDistribution> distributions = new ArrayList<>();
            ds.path("distribution").forEach(d -> {
                String dTitle = d.path("title").asText(null);
                String downloadUrl = d.path("downloadURL").asText(null);
                String accessUrl = d.path("accessURL").asText(null);
                String format = d.path("format").asText(null);
                String mediaType = d.path("mediaType").asText(null);
                if (downloadUrl != null || accessUrl != null) {
                    distributions.add(new HarvestDistribution(dTitle, downloadUrl, accessUrl, format, mediaType));
                }
            });

            String key = identifier != null ? identifier
                : (uri != null ? uri : title);

            if (key != null) {
                entities.add(new HarvestEntity(
                    key, HarvestEntityType.DATASET, uri,
                    title, description, null, null,
                    keywords, themes, distributions, List.of(), null, null
                ));
            }
        });
        log.info("Discovered {} datasets (POD format) from {}", entities.size(), baseUrl);
        return entities.stream();
    }

    // ── RDF / JSON-LD via Jena ─────────────────────────────────────────────

    private Stream<HarvestEntity> harvestRdf(String body, String baseUrl) {
        Model model = ModelFactory.createDefaultModel();
        RDFParser.source(baseUrl).lang(Lang.JSONLD).parse(model);
        log.debug("Loaded RDF model with {} triples from {}", model.size(), baseUrl);

        List<HarvestEntity> entities = new ArrayList<>();

        ResIterator typed = model.listResourcesWithProperty(
            model.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
            DCAT.Dataset
        );
        typed.forEachRemaining(r -> entities.add(toRdfEntity(r, model)));

        if (entities.isEmpty()) {
            Property datasetProp = model.createProperty(DCAT.NS, "dataset");
            model.listStatements(null, datasetProp, (RDFNode) null)
                .forEachRemaining(stmt -> {
                    if (stmt.getObject().isResource()) {
                        entities.add(toRdfEntity(stmt.getObject().asResource(), model));
                    }
                });
        }

        log.info("Discovered {} datasets (RDF format) from {}", entities.size(), baseUrl);
        return entities.stream();
    }

    private HarvestEntity toRdfEntity(Resource resource, Model model) {
        String uri = resource.isURIResource() ? resource.getURI()
            : getStringProperty(resource, DCTerms.identifier);
        String title = getStringProperty(resource, DCTerms.title);
        String description = getStringProperty(resource, DCTerms.description);
        return new HarvestEntity(
            uri != null ? uri : resource.toString(),
            HarvestEntityType.DATASET,
            uri != null ? uri : resource.toString(),
            title, description, null, null,
            extractKeywords(resource, model),
            extractThemes(resource, model),
            extractDistributions(resource, model),
            List.of(), null, null
        );
    }

    private List<HarvestDistribution> extractDistributions(Resource resource, Model model) {
        List<HarvestDistribution> result = new ArrayList<>();
        Property distributionProp = model.createProperty(DCAT.NS, "distribution");
        Property downloadUrlProp  = model.createProperty(DCAT.NS, "downloadURL");
        Property accessUrlProp    = model.createProperty(DCAT.NS, "accessURL");
        Property mediaTypeProp    = model.createProperty(DCAT.NS, "mediaType");

        resource.listProperties(distributionProp).forEachRemaining(stmt -> {
            if (!stmt.getObject().isResource()) return;
            Resource dist = stmt.getObject().asResource();
            String downloadUrl = getStringProperty(dist, downloadUrlProp);
            String accessUrl   = getStringProperty(dist, accessUrlProp);
            if (downloadUrl == null && accessUrl == null) return;
            result.add(new HarvestDistribution(
                getStringProperty(dist, DCTerms.title),
                downloadUrl,
                accessUrl,
                getStringProperty(dist, DCTerms.format),
                getStringProperty(dist, mediaTypeProp)
            ));
        });
        return result;
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private String fetchBody(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .header("Accept", "application/json, application/ld+json, */*")
            .build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    private String getStringProperty(Resource resource, Property property) {
        Statement stmt = resource.getProperty(property);
        if (stmt == null) return null;
        RDFNode node = stmt.getObject();
        if (node.isLiteral()) return node.asLiteral().getString();
        if (node.isURIResource()) return node.asResource().getURI();
        return node.toString();
    }

    private List<String> extractKeywords(Resource resource, Model model) {
        List<String> keywords = new ArrayList<>();
        resource.listProperties(DCAT.keyword)
            .forEachRemaining(stmt -> keywords.add(stmt.getLiteral().getString()));
        return keywords;
    }

    private List<String> extractThemes(Resource resource, Model model) {
        List<String> themes = new ArrayList<>();
        resource.listProperties(DCAT.theme)
            .forEachRemaining(stmt -> themes.add(stmt.getObject().toString()));
        return themes;
    }
}
