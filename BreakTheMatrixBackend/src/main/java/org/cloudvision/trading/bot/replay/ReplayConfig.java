package org.cloudvision.trading.bot.replay;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for a replay session
 */
public class ReplayConfig {
    
    private final String provider;
    private final String symbol;
    private final String interval;
    private final Instant startTime;
    private final Instant endTime;
    private final double speed; // Playback speed multiplier (1.0 = normal, 2.0 = 2x, etc.)
    
    private ReplayConfig(Builder builder) {
        this.provider = builder.provider;
        this.symbol = builder.symbol;
        this.interval = builder.interval;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.speed = builder.speed;
    }
    
    /**
     * Constructor for Jackson deserialization
     */
    @JsonCreator
    public ReplayConfig(
            @JsonProperty("provider") String provider,
            @JsonProperty("symbol") String symbol,
            @JsonProperty("interval") String interval,
            @JsonProperty("startTime") Instant startTime,
            @JsonProperty("endTime") Instant endTime,
            @JsonProperty("speed") Double speed) {
        this.provider = provider;
        this.symbol = symbol;
        this.interval = interval;
        this.startTime = startTime;
        this.endTime = endTime;
        this.speed = speed != null ? speed : 1.0;
    }
    
    // Getters
    public String getProvider() { return provider; }
    public String getSymbol() { return symbol; }
    public String getInterval() { return interval; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public double getSpeed() { return speed; }
    
    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String provider;
        private String symbol;
        private String interval;
        private Instant startTime;
        private Instant endTime;
        private double speed = 1.0;
        
        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }
        
        public Builder symbol(String symbol) {
            this.symbol = symbol;
            return this;
        }
        
        public Builder interval(String interval) {
            this.interval = interval;
            return this;
        }
        
        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }
        
        public Builder endTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }
        
        public Builder speed(double speed) {
            this.speed = speed;
            return this;
        }
        
        public ReplayConfig build() {
            // Validation
            if (provider == null || provider.isEmpty()) {
                throw new IllegalArgumentException("Provider is required");
            }
            if (symbol == null || symbol.isEmpty()) {
                throw new IllegalArgumentException("Symbol is required");
            }
            if (interval == null || interval.isEmpty()) {
                throw new IllegalArgumentException("Interval is required");
            }
            if (speed <= 0) {
                throw new IllegalArgumentException("Speed must be positive");
            }
            
            return new ReplayConfig(this);
        }
    }
}

