package org.cloudvision.trading.bot.visualization;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Map;

/**
 * Base interface for all visualization shapes that can be rendered on charts.
 * Shapes are visual elements like boxes, lines, markers, and arrows that overlay the chart.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = BoxShape.class, name = "box"),
    @JsonSubTypes.Type(value = LineShape.class, name = "line"),
    @JsonSubTypes.Type(value = MarkerShape.class, name = "marker"),
    @JsonSubTypes.Type(value = ArrowShape.class, name = "arrow"),
    @JsonSubTypes.Type(value = FillShape.class, name = "fill")
})
public interface Shape {
    /**
     * Get the shape type identifier
     */
    String getType();
    
    /**
     * Convert shape to a map representation for JSON serialization
     */
    Map<String, Object> toMap();
}

