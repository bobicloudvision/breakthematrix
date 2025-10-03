package org.cloudvision.trading.bot.indicators;

/**
 * Metadata for an indicator parameter
 * Describes configuration options for indicators
 */
public class IndicatorParameter {
    private final String name;
    private final String displayName;
    private final String description;
    private final ParameterType type;
    private final Object defaultValue;
    private final Object minValue;
    private final Object maxValue;
    private final boolean required;
    
    private IndicatorParameter(Builder builder) {
        this.name = builder.name;
        this.displayName = builder.displayName;
        this.description = builder.description;
        this.type = builder.type;
        this.defaultValue = builder.defaultValue;
        this.minValue = builder.minValue;
        this.maxValue = builder.maxValue;
        this.required = builder.required;
    }
    
    // Getters
    public String getName() { return name; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public ParameterType getType() { return type; }
    public Object getDefaultValue() { return defaultValue; }
    public Object getMinValue() { return minValue; }
    public Object getMaxValue() { return maxValue; }
    public boolean isRequired() { return required; }
    
    /**
     * Parameter types
     */
    public enum ParameterType {
        INTEGER,
        DECIMAL,
        STRING,
        BOOLEAN,
        ENUM
    }
    
    /**
     * Builder for IndicatorParameter
     */
    public static class Builder {
        private final String name;
        private String displayName;
        private String description;
        private ParameterType type = ParameterType.INTEGER;
        private Object defaultValue;
        private Object minValue;
        private Object maxValue;
        private boolean required = false;
        
        public Builder(String name) {
            this.name = name;
            this.displayName = name;
        }
        
        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder type(ParameterType type) {
            this.type = type;
            return this;
        }
        
        public Builder defaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }
        
        public Builder minValue(Object minValue) {
            this.minValue = minValue;
            return this;
        }
        
        public Builder maxValue(Object maxValue) {
            this.maxValue = maxValue;
            return this;
        }
        
        public Builder required(boolean required) {
            this.required = required;
            return this;
        }
        
        public IndicatorParameter build() {
            return new IndicatorParameter(this);
        }
    }
    
    public static Builder builder(String name) {
        return new Builder(name);
    }
}

