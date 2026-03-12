package com.example.aemassets.core.graph;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "AEM Assets Metadata - Graph Storage")
public @interface GraphStorageConfig {
    @AttributeDefinition(
            name = "Storage Mode",
            description = "Selects the graph storage adapter implementation."
    )
    String mode() default "in-memory";
}
