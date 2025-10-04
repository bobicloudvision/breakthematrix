package org.cloudvision.trading.bot.visualization;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry for shape types and their deduplication strategies.
 * Makes shape handling dynamic and extensible.
 */
public class ShapeRegistry {
    
    // Known shape types
    private static final Set<String> SHAPE_TYPES = new HashSet<>(Arrays.asList(
        "boxes", "lines", "markers", "arrows", "fills"
    ));
    
    // Deduplication key generators for each shape type
    private static final Map<String, Function<Map<String, Object>, String>> DEDUP_KEY_GENERATORS = new HashMap<>();
    
    static {
        // Box deduplication: time1 + price1 + price2
        DEDUP_KEY_GENERATORS.put("boxes", box -> 
            box.get("time1") + "_" + box.get("price1") + "_" + box.get("price2")
        );
        
        // Line deduplication: time1 + time2 + price1 + price2
        DEDUP_KEY_GENERATORS.put("lines", line -> 
            line.get("time1") + "_" + line.get("time2") + "_" + 
            line.get("price1") + "_" + line.get("price2")
        );
        
        // Marker deduplication: time + price
        DEDUP_KEY_GENERATORS.put("markers", marker -> 
            marker.get("time") + "_" + marker.get("price")
        );
        
        // Arrow deduplication: time + price + direction
        DEDUP_KEY_GENERATORS.put("arrows", arrow -> 
            arrow.get("time") + "_" + arrow.get("price") + "_" + arrow.get("direction")
        );
        
        // Fill deduplication: colorMode (typically only one fill config per indicator)
        DEDUP_KEY_GENERATORS.put("fills", fill -> 
            String.valueOf(fill.get("colorMode"))
        );
    }
    
    /**
     * Get all registered shape types
     */
    public static Set<String> getShapeTypes() {
        return new HashSet<>(SHAPE_TYPES);
    }
    
    /**
     * Check if a key is a known shape type
     */
    public static boolean isShapeType(String key) {
        return SHAPE_TYPES.contains(key);
    }
    
    /**
     * Register a new shape type
     */
    public static void registerShapeType(String shapeType, Function<Map<String, Object>, String> dedupKeyGenerator) {
        SHAPE_TYPES.add(shapeType);
        DEDUP_KEY_GENERATORS.put(shapeType, dedupKeyGenerator);
    }
    
    /**
     * Deduplicate shapes based on their type
     */
    public static List<Map<String, Object>> deduplicate(String shapeType, List<Map<String, Object>> shapes) {
        if (shapes == null || shapes.isEmpty()) {
            return new ArrayList<>();
        }
        
        Function<Map<String, Object>, String> keyGenerator = DEDUP_KEY_GENERATORS.get(shapeType);
        
        if (keyGenerator == null) {
            // No specific deduplication logic, return as is
            return new ArrayList<>(shapes);
        }
        
        // Deduplicate using the key generator
        Map<String, Map<String, Object>> uniqueMap = shapes.stream()
            .collect(Collectors.toMap(
                shape -> keyGenerator.apply(shape),
                shape -> shape,
                (existing, replacement) -> replacement, // Keep latest
                LinkedHashMap::new
            ));
        
        return new ArrayList<>(uniqueMap.values());
    }
    
    /**
     * Extract all shapes from additional data
     */
    public static Map<String, List<Map<String, Object>>> extractShapes(Map<String, Object> additionalData) {
        Map<String, List<Map<String, Object>>> shapesByType = new HashMap<>();
        
        if (additionalData == null) {
            return shapesByType;
        }
        
        // Check each known shape type
        for (String shapeType : SHAPE_TYPES) {
            if (additionalData.containsKey(shapeType)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> shapes = (List<Map<String, Object>>) additionalData.get(shapeType);
                if (shapes != null && !shapes.isEmpty()) {
                    shapesByType.put(shapeType, shapes);
                }
            }
        }
        
        return shapesByType;
    }
}

