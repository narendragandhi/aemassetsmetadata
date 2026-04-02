package com.semanticdam.core.core.graph;

import java.util.List;

public interface GraphSerializer {
    String toTurtle(List<GraphTriple> triples);
}
