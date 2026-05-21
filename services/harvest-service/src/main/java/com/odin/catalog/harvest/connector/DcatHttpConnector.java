package com.odin.catalog.harvest.connector;

import com.odin.catalog.harvest.domain.run.HarvestRun;
import com.odin.catalog.harvest.domain.source.HarvestSource;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.DCTerms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Component
public class DcatHttpConnector implements HarvestConnector {

    private static final Logger log = LoggerFactory.getLogger(DcatHttpConnector.class);

    @Override
    public String sourceType() {
        return "dcat_http";
    }

    @Override
    public boolean testConnection(HarvestSource source) {
        try {
            Model model = RDFDataMgr.loadModel(source.baseUrl());
            return !model.isEmpty();
        } catch (Exception e) {
            log.warn("DCAT HTTP connection test failed for {}: {}", source.baseUrl(), e.getMessage());
            return false;
        }
    }

    @Override
    public Stream<HarvestEntity> harvest(HarvestRun run, HarvestSource source) {
        log.info("Harvesting DCAT from {}", source.baseUrl());
        Model model = RDFDataMgr.loadModel(source.baseUrl());

        List<HarvestEntity> entities = new ArrayList<>();

        // Extract dcat:Dataset resources
        ResIterator datasets = model.listResourcesWithProperty(
            model.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
            DCAT.Dataset
        );

        datasets.forEachRemaining(resource -> {
            String uri = resource.getURI();
            String title = getStringProperty(resource, DCTerms.title);
            String description = getStringProperty(resource, DCTerms.description);

            entities.add(new HarvestEntity(
                uri, HarvestEntityType.DATASET, uri,
                title, description, null, null,
                extractKeywords(resource, model),
                extractThemes(resource, model),
                List.of(), null, null
            ));
        });

        log.info("Discovered {} datasets from {}", entities.size(), source.baseUrl());
        return entities.stream();
    }

    private String getStringProperty(Resource resource, Property property) {
        Statement stmt = resource.getProperty(property);
        if (stmt == null) return null;
        RDFNode node = stmt.getObject();
        return node.isLiteral() ? node.asLiteral().getString() : node.toString();
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
