package org.cloudvision.trading.bot.indicators;

import java.util.HashMap;
import java.util.Map;

/**
 * Abstract base class for indicators providing common functionality
 */
public abstract class AbstractIndicator implements Indicator {
    
    private final String id;
    private final String name;
    private final String description;
    private final IndicatorCategory category;
    
    protected AbstractIndicator(String id, String name, String description, IndicatorCategory category) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
    }
    
    @Override
    public String getId() {
        return id;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getDescription() {
        return description;
    }
    
    @Override
    public IndicatorCategory getCategory() {
        return category;
    }
    
    /**
     * Helper method to get integer parameter with default value
     */
    protected int getIntParameter(Map<String, Object> params, String key, int defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(value.toString());
    }
    
    /**
     * Helper method to get string parameter with default value
     */
    protected String getStringParameter(Map<String, Object> params, String key, String defaultValue) {
        Object value = params.get(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    /**
     * Helper method to get boolean parameter with default value
     */
    protected boolean getBooleanParameter(Map<String, Object> params, String key, boolean defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }
    
    /**
     * Helper method to get double parameter with default value
     */
    protected double getDoubleParameter(Map<String, Object> params, String key, double defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Double) {
            return (Double) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(value.toString());
    }
    
    /**
     * Create parameters map from defaults
     */
    protected Map<String, Object> mergeWithDefaults(Map<String, Object> params) {
        Map<String, Object> merged = new HashMap<>();
        
        // Add defaults
        for (Map.Entry<String, IndicatorParameter> entry : getParameters().entrySet()) {
            if (entry.getValue().getDefaultValue() != null) {
                merged.put(entry.getKey(), entry.getValue().getDefaultValue());
            }
        }
        
        // Override with provided params
        if (params != null) {
            merged.putAll(params);
        }
        
        return merged;
    }
}

