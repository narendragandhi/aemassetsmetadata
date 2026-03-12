package com.example.aemassets.core.graph;

import java.util.List;

public interface GraphSerializer {
    String toTurtle(List<GraphTriple> triples);
}
