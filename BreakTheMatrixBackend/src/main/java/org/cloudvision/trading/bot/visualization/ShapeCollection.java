package org.cloudvision.trading.bot.visualization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Collection of shapes organized by type for easy serialization to API responses.
 * This class helps indicators return properly formatted shapes.
 */
public class ShapeCollection {
    private final List<BoxShape> boxes = new ArrayList<>();
    private final List<LineShape> lines = new ArrayList<>();
    private final List<MarkerShape> markers = new ArrayList<>();
    private final List<ArrowShape> arrows = new ArrayList<>();
    
    public ShapeCollection addBox(BoxShape box) {
        boxes.add(box);
        return this;
    }
    
    public ShapeCollection addLine(LineShape line) {
        lines.add(line);
        return this;
    }
    
    public ShapeCollection addMarker(MarkerShape marker) {
        markers.add(marker);
        return this;
    }
    
    public ShapeCollection addArrow(ArrowShape arrow) {
        arrows.add(arrow);
        return this;
    }
    
    public List<BoxShape> getBoxes() {
        return boxes;
    }
    
    public List<LineShape> getLines() {
        return lines;
    }
    
    public List<MarkerShape> getMarkers() {
        return markers;
    }
    
    public List<ArrowShape> getArrows() {
        return arrows;
    }
    
    public boolean isEmpty() {
        return boxes.isEmpty() && lines.isEmpty() && markers.isEmpty() && arrows.isEmpty();
    }
    
    /**
     * Convert to map format for API response
     * Returns a map like: { "boxes": [...], "lines": [...], "markers": [...], "arrows": [...] }
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        
        if (!boxes.isEmpty()) {
            map.put("boxes", boxes.stream()
                .map(BoxShape::toMap)
                .collect(Collectors.toList()));
        }
        
        if (!lines.isEmpty()) {
            map.put("lines", lines.stream()
                .map(LineShape::toMap)
                .collect(Collectors.toList()));
        }
        
        if (!markers.isEmpty()) {
            map.put("markers", markers.stream()
                .map(MarkerShape::toMap)
                .collect(Collectors.toList()));
        }
        
        if (!arrows.isEmpty()) {
            map.put("arrows", arrows.stream()
                .map(ArrowShape::toMap)
                .collect(Collectors.toList()));
        }
        
        return map;
    }
    
    /**
     * Create a list of all shape maps for flat serialization
     */
    public List<Map<String, Object>> toList() {
        List<Map<String, Object>> allShapes = new ArrayList<>();
        
        boxes.forEach(box -> allShapes.add(box.toMap()));
        lines.forEach(line -> allShapes.add(line.toMap()));
        markers.forEach(marker -> allShapes.add(marker.toMap()));
        arrows.forEach(arrow -> allShapes.add(arrow.toMap()));
        
        return allShapes;
    }
}

