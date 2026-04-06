/*
 *  Copyright 2015 Adobe Systems Incorporated
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.semanticdam.core.core.models;

import static org.apache.sling.api.resource.ResourceResolver.PROPERTY_RESOURCE_TYPE;

import javax.annotation.PostConstruct;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.InjectionStrategy;
import org.apache.sling.models.annotations.injectorspecific.OSGiService;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;

import com.semanticdam.core.core.domain.AssetId;
import com.semanticdam.core.core.graph.KnowledgeGraphService;
import java.util.Collection;
import java.util.Optional;

@Model(adaptables = Resource.class)
public class HelloWorldModel {

    @OSGiService
    private KnowledgeGraphService knowledgeGraphService;

    @OSGiService
    private com.semanticdam.core.core.application.SemanticSeoService seoService;

    @ValueMapValue(name=PROPERTY_RESOURCE_TYPE, injectionStrategy=InjectionStrategy.OPTIONAL)
    @Default(values="No resourceType")
    protected String resourceType;

    @SlingObject
    private Resource currentResource;

    @SlingObject
    private ResourceResolver resourceResolver;

    private String message;
    private String seoScript;

    @PostConstruct
    protected void init() {
        PageManager pageManager = resourceResolver.adaptTo(PageManager.class);
        String currentPagePath = Optional.ofNullable(pageManager)
                .map(pm -> pm.getContainingPage(currentResource))
                .map(Page::getPath).orElse("");

        int suggestionCount = 0;
        if (currentResource.getPath().startsWith("/content/dam")) {
            String assetPath = currentResource.getPath();
            suggestionCount = knowledgeGraphService.findRelated(AssetId.of(assetPath)).size();
            seoScript = seoService.getJsonLd(assetPath);
        }

        message = "Hello World!\n"
            + "Resource type is: " + resourceType + "\n"
            + "Current page is:  " + currentPagePath + "\n"
            + "Semantic Intelligence: " + (suggestionCount > 0 ? "Found " + suggestionCount + " smart suggestions!" : "Scanning graph...");
    }

    public String getMessage() {
        return message;
    }

    public String getSeoScript() {
        return seoScript;
    }

}
