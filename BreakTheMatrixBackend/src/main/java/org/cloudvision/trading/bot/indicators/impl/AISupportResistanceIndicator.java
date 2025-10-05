package org.cloudvision.trading.bot.indicators.impl;

import org.cloudvision.trading.bot.indicators.*;
import org.cloudvision.trading.bot.strategy.IndicatorMetadata;
import org.cloudvision.trading.bot.visualization.BoxShape;
import org.cloudvision.trading.model.CandlestickData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI-Powered Support and Resistance Zones Indicator
 * 
 * Uses advanced clustering algorithms and machine learning techniques to identify
 * high-probability support and resistance zones based on:
 * - Price density clustering (where price spends time)
 * - Volume-weighted price levels (where significant trading occurs)
 * - Rejection analysis (where price gets rejected)
 * - Historical touch strength (how many times price reacts to a level)
 * 
 * ALGORITHM:
 * 1. Collect price data from highs, lows, and closes with volume weights
 * 2. Apply kernel density estimation to find price clusters
 * 3. Use volume profile to weight zones by trading activity
 * 4. Detect price rejections (wicks) to identify strong zones
 * 5. Score zones based on multiple factors (touches, volume, rejections, recency)
 * 6. Dynamically merge nearby zones
 * 7. Display top N zones as colored boxes with strength indicators
 * 
 * FEATURES:
 * - Auto-adjusts to market conditions
 * - Volume-weighted zone detection
 * - Dynamic zone strength scoring
 * - Adaptive zone merging
 * - Historical learning from price action
 */
@Component
public class AISupportResistanceIndicator extends AbstractIndicator {
    
    /**
     * Price data point for clustering
     */
    private static class PricePoint {
        BigDecimal price;
        BigDecimal volume;
        Instant timestamp;
        String type; // "high", "low", "close", "rejection"
        BigDecimal rejectionStrength; // For wick-based rejections
        
        PricePoint(BigDecimal price, BigDecimal volume, Instant timestamp, String type) {
            this.price = price;
            this.volume = volume;
            this.timestamp = timestamp;
            this.type = type;
            this.rejectionStrength = BigDecimal.ZERO;
        }
    }
    
    /**
     * AI-detected zone
     */
    public static class AIZone {
        BigDecimal centerPrice;
        BigDecimal topPrice;
        BigDecimal bottomPrice;
        Instant firstDetected;
        Instant lastTouched;
        int touchCount;
        BigDecimal totalVolume;
        BigDecimal avgRejectionStrength;
        double strengthScore; // Composite AI score
        boolean isSupport;
        List<PricePoint> contributingPoints;
        
        AIZone(BigDecimal center, BigDecimal width, boolean isSupport) {
            this.centerPrice = center;
            this.topPrice = center.add(width.divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP));
            this.bottomPrice = center.subtract(width.divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP));
            this.isSupport = isSupport;
            this.touchCount = 0;
            this.totalVolume = BigDecimal.ZERO;
            this.avgRejectionStrength = BigDecimal.ZERO;
            this.strengthScore = 0.0;
            this.contributingPoints = new ArrayList<>();
        }
    }
    
    /**
     * State for progressive calculation
     */
    public static class AIState {
        List<PricePoint> priceHistory = new ArrayList<>();
        List<AIZone> detectedZones = new ArrayList<>();
        List<CandlestickData> recentCandles = new ArrayList<>();
        int barIndex = 0;
        BigDecimal minPrice = null;
        BigDecimal maxPrice = null;
    }
    
    public AISupportResistanceIndicator() {
        super("ai_sr_zones", "AI Support/Resistance Zones", 
              "Advanced AI-powered support and resistance zone detection using clustering and volume analysis",
              Indicator.IndicatorCategory.CUSTOM);
    }
    
    @Override
    public Map<String, IndicatorParameter> getParameters() {
        Map<String, IndicatorParameter> params = new HashMap<>();
        
        params.put("lookbackPeriod", IndicatorParameter.builder("lookbackPeriod")
            .displayName("Lookback Period")
            .description("Number of candles to analyze for zone detection")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(200)
            .minValue(50)
            .maxValue(1000)
            .required(true)
            .build());
        
        params.put("maxZones", IndicatorParameter.builder("maxZones")
            .displayName("Max Zones")
            .description("Maximum number of zones to display")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(3)
            .minValue(1)
            .maxValue(20)
            .required(true)
            .build());
        
        params.put("zoneWidthPercent", IndicatorParameter.builder("zoneWidthPercent")
            .displayName("Line Thickness %")
            .description("Thickness of support/resistance lines (very small = thin line)")
            .type(IndicatorParameter.ParameterType.DECIMAL)
            .defaultValue(0.15)
            .minValue(0.05)
            .maxValue(1.0)
            .required(true)
            .build());
        
        params.put("clusterSensitivity", IndicatorParameter.builder("clusterSensitivity")
            .displayName("Cluster Sensitivity")
            .description("How sensitive clustering is (lower = fewer, stronger zones)")
            .type(IndicatorParameter.ParameterType.DECIMAL)
            .defaultValue(1.0)
            .minValue(0.5)
            .maxValue(5.0)
            .required(true)
            .build());
        
        params.put("minStrengthThreshold", IndicatorParameter.builder("minStrengthThreshold")
            .displayName("Min Strength Threshold")
            .description("Minimum strength score to display a zone (0-1, 0.65 = 65%)")
            .type(IndicatorParameter.ParameterType.DECIMAL)
            .defaultValue(0.65)
            .minValue(0.0)
            .maxValue(1.0)
            .required(true)
            .build());
        
        params.put("minTouches", IndicatorParameter.builder("minTouches")
            .displayName("Min Touches")
            .description("Minimum number of touches required for a valid zone")
            .type(IndicatorParameter.ParameterType.INTEGER)
            .defaultValue(3)
            .minValue(1)
            .maxValue(10)
            .required(true)
            .build());
        
        params.put("minZoneDistance", IndicatorParameter.builder("minZoneDistance")
            .displayName("Min Zone Distance %")
            .description("Minimum distance between zones as percentage of price (prevents overlap)")
            .type(IndicatorParameter.ParameterType.DECIMAL)
            .defaultValue(5.0)
            .minValue(2.0)
            .maxValue(15.0)
            .required(true)
            .build());
        
        params.put("volumeWeight", IndicatorParameter.builder("volumeWeight")
            .displayName("Volume Weight")
            .description("Importance of volume in zone scoring (0-1)")
            .type(IndicatorParameter.ParameterType.DECIMAL)
            .defaultValue(0.3)
            .minValue(0.0)
            .maxValue(1.0)
            .required(true)
            .build());
        
        params.put("rejectionWeight", IndicatorParameter.builder("rejectionWeight")
            .displayName("Rejection Weight")
            .description("Importance of price rejections in zone scoring (0-1)")
            .type(IndicatorParameter.ParameterType.DECIMAL)
            .defaultValue(0.4)
            .minValue(0.0)
            .maxValue(1.0)
            .required(true)
            .build());
        
        params.put("touchWeight", IndicatorParameter.builder("touchWeight")
            .displayName("Touch Weight")
            .description("Importance of touch count in zone scoring (0-1)")
            .type(IndicatorParameter.ParameterType.DECIMAL)
            .defaultValue(0.3)
            .minValue(0.0)
            .maxValue(1.0)
            .required(true)
            .build());
        
        params.put("supportColor", IndicatorParameter.builder("supportColor")
            .displayName("Support Color")
            .description("Color for support lines")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("rgba(0, 255, 0, 0.35)")
            .required(false)
            .build());
        
        params.put("resistanceColor", IndicatorParameter.builder("resistanceColor")
            .displayName("Resistance Color")
            .description("Color for resistance lines")
            .type(IndicatorParameter.ParameterType.STRING)
            .defaultValue("rgba(255, 0, 0, 0.35)")
            .required(false)
            .build());
        
        params.put("showLabels", IndicatorParameter.builder("showLabels")
            .displayName("Show Labels")
            .description("Display zone strength labels")
            .type(IndicatorParameter.ParameterType.BOOLEAN)
            .defaultValue(true)
            .required(false)
            .build());
        
        return params;
    }
    
    @Override
    public Object onInit(List<CandlestickData> historicalCandles, Map<String, Object> params) {
        validateParameters(params);
        params = mergeWithDefaults(params);
        
        AIState state = new AIState();
        
        // Process historical candles
        if (historicalCandles != null && !historicalCandles.isEmpty()) {
            int lookback = getIntParameter(params, "lookbackPeriod", 200);
            
            // Take only the last N candles for initial processing
            int startIdx = Math.max(0, historicalCandles.size() - lookback);
            for (int i = startIdx; i < historicalCandles.size(); i++) {
                CandlestickData candle = historicalCandles.get(i);
                collectPricePoints(candle, state);
                state.recentCandles.add(candle);
                state.barIndex++;
            }
            
            // Perform initial zone detection
            detectZones(state, params);
        }
        
        return state;
    }
    
    @Override
    public Map<String, Object> onNewCandle(CandlestickData candle, Map<String, Object> params, Object stateObj) {
        if (candle == null) {
            throw new IllegalArgumentException("Candle cannot be null");
        }
        
        params = mergeWithDefaults(params);
        AIState state = (stateObj instanceof AIState) ? (AIState) stateObj : new AIState();
        
        // Add new candle data
        collectPricePoints(candle, state);
        state.recentCandles.add(candle);
        state.barIndex++;
        
        // Maintain lookback window
        int lookback = getIntParameter(params, "lookbackPeriod", 200);
        if (state.recentCandles.size() > lookback) {
            state.recentCandles.remove(0);
        }
        if (state.priceHistory.size() > lookback * 3) { // Keep more price points
            state.priceHistory.subList(0, state.priceHistory.size() - lookback * 3).clear();
        }
        
        // Re-detect zones every 10 candles for performance (reduced from 5 to minimize box creation)
        boolean zonesChanged = false;
        if (state.barIndex % 10 == 0) {
            detectZones(state, params);
            scoreZones(state, params, candle.getClose());
            zonesChanged = true;
        } else {
            // Just update existing zones with current candle (no new zones created)
            updateZones(state, candle, params);
            // Update support/resistance classification based on current price
            for (AIZone zone : state.detectedZones) {
                zone.isSupport = zone.centerPrice.compareTo(candle.getClose()) < 0;
            }
        }
        
        // Build output - only create boxes if zones changed OR every 10 candles to update time extent
        Map<String, BigDecimal> values = calculateOutputValues(state, candle.getClose());
        List<Map<String, Object>> boxes = (state.barIndex % 10 == 0) ? 
            convertZonesToBoxes(state, candle, params) : new ArrayList<>();
        
        Map<String, Object> result = new HashMap<>();
//        result.put("values", values);
        result.put("state", state);
        result.put("boxes", boxes);
        
        return result;
    }
    
    /**
     * Collect price points from a candle for clustering
     */
    private void collectPricePoints(CandlestickData candle, AIState state) {
        BigDecimal volume = candle.getVolume();
        Instant time = candle.getCloseTime();
        
        // Add high as potential resistance
        PricePoint high = new PricePoint(candle.getHigh(), volume, time, "high");
        state.priceHistory.add(high);
        
        // Add low as potential support
        PricePoint low = new PricePoint(candle.getLow(), volume, time, "low");
        state.priceHistory.add(low);
        
        // Add close with higher weight
        PricePoint close = new PricePoint(candle.getClose(), 
            volume.multiply(BigDecimal.valueOf(1.5)), time, "close");
        state.priceHistory.add(close);
        
        // Detect and add rejection points (wicks)
        BigDecimal bodyTop = candle.getClose().max(candle.getOpen());
        BigDecimal bodyBottom = candle.getClose().min(candle.getOpen());
        BigDecimal bodySize = bodyTop.subtract(bodyBottom);
        
        // Upper wick rejection (resistance)
        BigDecimal upperWick = candle.getHigh().subtract(bodyTop);
        if (bodySize.compareTo(BigDecimal.ZERO) > 0 && 
            upperWick.compareTo(bodySize.multiply(BigDecimal.valueOf(1.5))) > 0) {
            PricePoint rejection = new PricePoint(candle.getHigh(), 
                volume.multiply(BigDecimal.valueOf(2)), time, "rejection");
            rejection.rejectionStrength = upperWick.divide(bodySize, 2, RoundingMode.HALF_UP);
            state.priceHistory.add(rejection);
        }
        
        // Lower wick rejection (support)
        BigDecimal lowerWick = bodyBottom.subtract(candle.getLow());
        if (bodySize.compareTo(BigDecimal.ZERO) > 0 && 
            lowerWick.compareTo(bodySize.multiply(BigDecimal.valueOf(1.5))) > 0) {
            PricePoint rejection = new PricePoint(candle.getLow(), 
                volume.multiply(BigDecimal.valueOf(2)), time, "rejection");
            rejection.rejectionStrength = lowerWick.divide(bodySize, 2, RoundingMode.HALF_UP);
            state.priceHistory.add(rejection);
        }
        
        // Update price range
        if (state.minPrice == null || candle.getLow().compareTo(state.minPrice) < 0) {
            state.minPrice = candle.getLow();
        }
        if (state.maxPrice == null || candle.getHigh().compareTo(state.maxPrice) > 0) {
            state.maxPrice = candle.getHigh();
        }
    }
    
    /**
     * Detect zones using clustering and kernel density estimation
     */
    private void detectZones(AIState state, Map<String, Object> params) {
        if (state.priceHistory.isEmpty() || state.minPrice == null || state.maxPrice == null) {
            return;
        }
        
        double sensitivity = getDoubleParameter(params, "clusterSensitivity", 1.0);
        double zoneWidthPct = getDoubleParameter(params, "zoneWidthPercent", 0.15);
        int minTouches = getIntParameter(params, "minTouches", 3);
        
        // Create price density map
        Map<BigDecimal, PriceCluster> densityMap = createPriceDensityMap(state, sensitivity);
        
        // Find local maxima (peaks in density)
        List<PriceCluster> peaks = findDensityPeaks(densityMap, sensitivity, minTouches);
        
        // Convert peaks to zones (thin lines)
        state.detectedZones.clear();
        for (PriceCluster peak : peaks) {
            // Use a very thin width for line-like appearance (human-drawn style)
            // This makes zones look like thin horizontal lines instead of thick boxes
            BigDecimal zoneWidth = peak.centerPrice.multiply(BigDecimal.valueOf(zoneWidthPct / 100.0));
            
            // Ensure minimum visibility but keep it thin (0.08% minimum for thin line look)
            BigDecimal minWidth = peak.centerPrice.multiply(BigDecimal.valueOf(0.0008));
            zoneWidth = zoneWidth.max(minWidth);
            
            // Cap maximum width to keep it thin even if user sets high percentage
            BigDecimal maxWidth = peak.centerPrice.multiply(BigDecimal.valueOf(0.01)); // Max 1%
            zoneWidth = zoneWidth.min(maxWidth);
            
            // Determine if support or resistance based on position relative to current price
            boolean isSupport = true; // Will be determined during scoring
            
            AIZone zone = new AIZone(peak.centerPrice, zoneWidth, isSupport);
            zone.touchCount = peak.pointCount;
            zone.totalVolume = peak.totalVolume;
            zone.avgRejectionStrength = peak.avgRejection;
            zone.firstDetected = peak.firstSeen;
            zone.lastTouched = peak.lastSeen;
            zone.contributingPoints = new ArrayList<>(peak.points);
            
            state.detectedZones.add(zone);
        }
    }
    
    /**
     * Calculate the actual price spread within a cluster
     */
    private BigDecimal calculateClusterSpread(PriceCluster cluster) {
        if (cluster.points.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        // Find min and max prices in the cluster
        BigDecimal minPrice = cluster.points.get(0).price;
        BigDecimal maxPrice = cluster.points.get(0).price;
        
        for (PricePoint point : cluster.points) {
            if (point.price.compareTo(minPrice) < 0) {
                minPrice = point.price;
            }
            if (point.price.compareTo(maxPrice) > 0) {
                maxPrice = point.price;
            }
        }
        
        // Calculate spread with some padding (1.5x the actual spread for better visibility)
        BigDecimal spread = maxPrice.subtract(minPrice);
        return spread.multiply(BigDecimal.valueOf(1.5));
    }
    
    /**
     * Create price density map using kernel density estimation
     */
    private Map<BigDecimal, PriceCluster> createPriceDensityMap(AIState state, double sensitivity) {
        Map<BigDecimal, PriceCluster> clusters = new TreeMap<>();
        
        BigDecimal priceRange = state.maxPrice.subtract(state.minPrice);
        BigDecimal clusterThreshold = priceRange.multiply(
            BigDecimal.valueOf(sensitivity / 100.0)
        );
        
        for (PricePoint point : state.priceHistory) {
            // Find or create cluster
            PriceCluster targetCluster = null;
            BigDecimal closestDistance = null;
            
            // Find the closest cluster within threshold
            for (PriceCluster cluster : clusters.values()) {
                BigDecimal distance = point.price.subtract(cluster.centerPrice).abs();
                if (distance.compareTo(clusterThreshold) <= 0) {
                    if (closestDistance == null || distance.compareTo(closestDistance) < 0) {
                        targetCluster = cluster;
                        closestDistance = distance;
                    }
                }
            }
            
            if (targetCluster == null) {
                // Create new cluster
                targetCluster = new PriceCluster(point.price);
                clusters.put(point.price, targetCluster);
            }
            
            // Add point to cluster
            targetCluster.addPoint(point);
        }
        
        // Merge clusters that are too close together
        clusters = mergeSimilarClusters(clusters, priceRange);
        
        return clusters;
    }
    
    /**
     * Merge clusters that are very close together
     */
    private Map<BigDecimal, PriceCluster> mergeSimilarClusters(Map<BigDecimal, PriceCluster> clusters,
                                                                BigDecimal priceRange) {
        // Minimum distance between clusters (3.5% of price range for strong separation)
        BigDecimal minDistance = priceRange.multiply(BigDecimal.valueOf(0.035));
        
        List<PriceCluster> clusterList = new ArrayList<>(clusters.values());
        clusterList.sort((a, b) -> a.centerPrice.compareTo(b.centerPrice));
        
        Map<BigDecimal, PriceCluster> mergedClusters = new TreeMap<>();
        
        int i = 0;
        while (i < clusterList.size()) {
            PriceCluster current = clusterList.get(i);
            
            // Look ahead to merge nearby clusters
            List<PriceCluster> toMerge = new ArrayList<>();
            toMerge.add(current);
            
            int j = i + 1;
            while (j < clusterList.size()) {
                PriceCluster next = clusterList.get(j);
                BigDecimal distance = next.centerPrice.subtract(current.centerPrice);
                
                if (distance.compareTo(minDistance) <= 0) {
                    toMerge.add(next);
                    j++;
                } else {
                    break;
                }
            }
            
            // Merge if we found nearby clusters
            if (toMerge.size() > 1) {
                PriceCluster merged = mergeClusters(toMerge);
                mergedClusters.put(merged.centerPrice, merged);
            } else {
                mergedClusters.put(current.centerPrice, current);
            }
            
            i = j;
        }
        
        return mergedClusters;
    }
    
    /**
     * Merge multiple clusters into one
     */
    private PriceCluster mergeClusters(List<PriceCluster> clusters) {
        // Calculate weighted average center
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal weightedSum = BigDecimal.ZERO;
        
        for (PriceCluster cluster : clusters) {
            BigDecimal weight = cluster.totalVolume;
            weightedSum = weightedSum.add(cluster.centerPrice.multiply(weight));
            totalWeight = totalWeight.add(weight);
        }
        
        BigDecimal newCenter = totalWeight.compareTo(BigDecimal.ZERO) > 0 ?
            weightedSum.divide(totalWeight, 8, RoundingMode.HALF_UP) :
            clusters.get(0).centerPrice;
        
        PriceCluster merged = new PriceCluster(newCenter);
        
        // Combine all points
        for (PriceCluster cluster : clusters) {
            for (PricePoint point : cluster.points) {
                merged.addPoint(point);
            }
        }
        
        return merged;
    }
    
    /**
     * Find peaks in the density map
     */
    private List<PriceCluster> findDensityPeaks(Map<BigDecimal, PriceCluster> densityMap, 
                                                  double sensitivity, int minTouches) {
        List<PriceCluster> allClusters = new ArrayList<>(densityMap.values());
        
        // Sort by density (volume-weighted point count)
        allClusters.sort((a, b) -> {
            double densityA = a.totalVolume.doubleValue() * a.pointCount;
            double densityB = b.totalVolume.doubleValue() * b.pointCount;
            return Double.compare(densityB, densityA);
        });
        
        // Return top clusters that meet minimum requirements
        List<PriceCluster> peaks = new ArrayList<>();
        for (PriceCluster cluster : allClusters) {
            if (cluster.pointCount >= minTouches) { // Use configurable minimum touches
                peaks.add(cluster);
            }
        }
        
        return peaks;
    }
    
    /**
     * Update existing zones with new candle data
     */
    private void updateZones(AIState state, CandlestickData candle, Map<String, Object> params) {
        BigDecimal close = candle.getClose();
        BigDecimal high = candle.getHigh();
        BigDecimal low = candle.getLow();
        
        for (AIZone zone : state.detectedZones) {
            // Check if price touched this zone
            if ((high.compareTo(zone.bottomPrice) >= 0 && low.compareTo(zone.topPrice) <= 0)) {
                zone.touchCount++;
                zone.lastTouched = candle.getCloseTime();
                zone.totalVolume = zone.totalVolume.add(candle.getVolume());
            }
        }
    }
    
    /**
     * Score zones using AI-inspired multi-factor analysis
     */
    private void scoreZones(AIState state, Map<String, Object> params, BigDecimal currentPrice) {
        double volumeWeight = getDoubleParameter(params, "volumeWeight", 0.3);
        double rejectionWeight = getDoubleParameter(params, "rejectionWeight", 0.4);
        double touchWeight = getDoubleParameter(params, "touchWeight", 0.3);
        int maxZones = getIntParameter(params, "maxZones", 3);
        double minStrength = getDoubleParameter(params, "minStrengthThreshold", 0.65);
        
        // Normalize factors
        double maxVolume = state.detectedZones.stream()
            .mapToDouble(z -> z.totalVolume.doubleValue())
            .max().orElse(1.0);
        
        int maxTouches = state.detectedZones.stream()
            .mapToInt(z -> z.touchCount)
            .max().orElse(1);
        
        double maxRejection = state.detectedZones.stream()
            .mapToDouble(z -> z.avgRejectionStrength.doubleValue())
            .max().orElse(1.0);
        
        // Calculate composite scores
        for (AIZone zone : state.detectedZones) {
            double volumeScore = zone.totalVolume.doubleValue() / maxVolume;
            double touchScore = (double) zone.touchCount / maxTouches;
            double rejectionScore = maxRejection > 0 ? 
                zone.avgRejectionStrength.doubleValue() / maxRejection : 0;
            
            // Recency factor (decay over time)
            long ageMinutes = (Instant.now().getEpochSecond() - 
                zone.lastTouched.getEpochSecond()) / 60;
            double recencyFactor = Math.exp(-ageMinutes / 10000.0); // Slow decay
            
            // Composite AI score
            zone.strengthScore = (volumeScore * volumeWeight +
                                 touchScore * touchWeight +
                                 rejectionScore * rejectionWeight) * recencyFactor;
            
            // Determine if support or resistance based on current price
            zone.isSupport = zone.centerPrice.compareTo(currentPrice) < 0;
        }
        
        // Filter by minimum strength threshold
        state.detectedZones = state.detectedZones.stream()
            .filter(zone -> zone.strengthScore >= minStrength)
            .collect(Collectors.toList());
        
        // Sort by strength and keep only top N zones
        state.detectedZones.sort((a, b) -> Double.compare(b.strengthScore, a.strengthScore));
        if (state.detectedZones.size() > maxZones) {
            state.detectedZones = new ArrayList<>(state.detectedZones.subList(0, maxZones));
        }
        
        // Separate supports and resistances
        List<AIZone> supports = state.detectedZones.stream()
            .filter(z -> z.isSupport)
            .sorted((a, b) -> Double.compare(b.strengthScore, a.strengthScore))
            .collect(Collectors.toList());
        
        List<AIZone> resistances = state.detectedZones.stream()
            .filter(z -> !z.isSupport)
            .sorted((a, b) -> Double.compare(b.strengthScore, a.strengthScore))
            .collect(Collectors.toList());
        
        // Remove overlapping within each group FIRST (more aggressive)
        supports = removeOverlappingZones(supports, params, currentPrice);
        resistances = removeOverlappingZones(resistances, params, currentPrice);
        
        // Limit to max zones per type
        int maxPerType = Math.max(1, maxZones / 2);
        supports = supports.stream().limit(maxPerType).collect(Collectors.toList());
        resistances = resistances.stream().limit(maxPerType).collect(Collectors.toList());
        
        // Combine final zones
        List<AIZone> finalZones = new ArrayList<>();
        finalZones.addAll(supports);
        finalZones.addAll(resistances);
        
        state.detectedZones = finalZones;
    }
    
    /**
     * Remove overlapping zones, keeping only the strongest ones
     * AGGRESSIVE deduplication - ensures only ONE zone per price area
     */
    private List<AIZone> removeOverlappingZones(List<AIZone> zones, Map<String, Object> params, 
                                                 BigDecimal currentPrice) {
        if (zones.size() <= 1) {
            return zones;
        }
        
        double minDistancePct = getDoubleParameter(params, "minZoneDistance", 5.0);
        
        // Sort zones by STRENGTH (strongest first) - this ensures we keep the best zones
        zones.sort((a, b) -> Double.compare(b.strengthScore, a.strengthScore));
        
        List<AIZone> nonOverlapping = new ArrayList<>();
        
        for (AIZone zone : zones) {
            boolean tooClose = false;
            
            // Check if this zone is too close to ANY already selected zone
            for (AIZone selected : nonOverlapping) {
                if (zonesOverlap(zone, selected, minDistancePct)) {
                    tooClose = true;
                    break;
                }
            }
            
            // Only add if it's sufficiently far from all selected zones
            if (!tooClose) {
                nonOverlapping.add(zone);
            }
        }
        
        // Sort by price for cleaner display (resistance on top, support on bottom)
        nonOverlapping.sort((a, b) -> b.centerPrice.compareTo(a.centerPrice));
        
        return nonOverlapping;
    }
    
    /**
     * Check if two zones overlap or are too close
     * STRICT check - even zones that are "close" are considered overlapping
     */
    private boolean zonesOverlap(AIZone zone1, AIZone zone2, double minDistancePct) {
        // Calculate the distance between zone centers
        BigDecimal distance = zone1.centerPrice.subtract(zone2.centerPrice).abs();
        
        // Calculate minimum required distance (use higher price for percentage calculation)
        BigDecimal higherPrice = zone1.centerPrice.max(zone2.centerPrice);
        BigDecimal minDistance = higherPrice.multiply(
            BigDecimal.valueOf(minDistancePct / 100.0)
        );
        
        // STRICT: If distance is less than minimum, they're too close
        // This ensures strong separation between zones
        return distance.compareTo(minDistance) < 0;
    }
    
    /**
     * Calculate output values for the indicator
     */
    private Map<String, BigDecimal> calculateOutputValues(AIState state, BigDecimal currentPrice) {
        Map<String, BigDecimal> values = new HashMap<>();
        
        // Find nearest support and resistance
        BigDecimal nearestSupport = null;
        BigDecimal nearestResistance = null;
        double supportStrength = 0;
        double resistanceStrength = 0;
        
        for (AIZone zone : state.detectedZones) {
            if (zone.isSupport && zone.centerPrice.compareTo(currentPrice) < 0) {
                if (nearestSupport == null || zone.centerPrice.compareTo(nearestSupport) > 0) {
                    nearestSupport = zone.centerPrice;
                    supportStrength = zone.strengthScore;
                }
            } else if (!zone.isSupport && zone.centerPrice.compareTo(currentPrice) > 0) {
                if (nearestResistance == null || zone.centerPrice.compareTo(nearestResistance) < 0) {
                    nearestResistance = zone.centerPrice;
                    resistanceStrength = zone.strengthScore;
                }
            }
        }
        
        values.put("nearestSupport", nearestSupport != null ? nearestSupport : BigDecimal.ZERO);
        values.put("nearestResistance", nearestResistance != null ? nearestResistance : BigDecimal.ZERO);
        values.put("supportStrength", BigDecimal.valueOf(supportStrength));
        values.put("resistanceStrength", BigDecimal.valueOf(resistanceStrength));
        values.put("totalZones", BigDecimal.valueOf(state.detectedZones.size()));
        
        return values;
    }
    
    /**
     * Convert zones to boxes for visualization
     * Each box gets a unique ID and STABLE time range to prevent accumulation
     */
    private List<Map<String, Object>> convertZonesToBoxes(AIState state, CandlestickData currentCandle,
                                                           Map<String, Object> params) {
        List<Map<String, Object>> boxes = new ArrayList<>();
        
        String supportColor = getStringParameter(params, "supportColor", "rgba(0, 255, 0, 0.35)");
        String resistanceColor = getStringParameter(params, "resistanceColor", "rgba(255, 0, 0, 0.35)");
        boolean showLabels = getBooleanParameter(params, "showLabels", true);
        
        long currentTime = currentCandle.getCloseTime().getEpochSecond();
        
        for (AIZone zone : state.detectedZones) {
            // Adjust opacity based on strength (higher for thin lines to be visible)
            String color = zone.isSupport ? supportColor : resistanceColor;
            double opacity = 0.25 + (zone.strengthScore * 0.35); // 0.25 to 0.60 opacity for visibility
            color = adjustOpacity(color, opacity);
            
            // Slightly visible borders for thin line effect
            String borderColor = zone.isSupport ? 
                "rgba(0, 255, 0, 0.5)" : "rgba(255, 0, 0, 0.5)";
            
            // Create label
            String label = null;
            if (showLabels) {
                label = String.format("%s (%.1f%%)", 
                    zone.isSupport ? "SUP" : "RES", 
                    zone.strengthScore * 100);
            }
            
            // Create unique ID based on zone price (rounded to 0 decimal places for stability)
            // This ensures the same zone always gets the same ID
            String uniqueId = String.format("ai_sr_zone_%s_%d", 
                zone.isSupport ? "sup" : "res",
                zone.centerPrice.setScale(0, RoundingMode.HALF_UP).longValue());
            
            // Use STABLE time range that doesn't change:
            // - Start from zone's first detection
            // - End at a FIXED future time (rounded to prevent constant changes)
            long startTime = zone.firstDetected != null ? 
                zone.firstDetected.getEpochSecond() : 
                (currentTime - 3600); // 1 hour back if no first detected
            
            // Round end time to next midnight + 7 days (stable, doesn't change on every candle)
            long roundedCurrentTime = (currentTime / 86400) * 86400; // Round to start of day
            long endTime = roundedCurrentTime + (86400 * 7); // 7 days from start of current day
            
            // Use ONLY the center price for both top and bottom to create a single horizontal line
            BigDecimal centerPrice = zone.centerPrice.setScale(2, RoundingMode.HALF_UP);
            
            BoxShape box = BoxShape.builder()
                .time1(startTime)
                .time2(endTime)
                .price1(centerPrice)  // Same price for both
                .price2(centerPrice)  // Creates a horizontal line
                .color(color)
                .borderColor(borderColor)
                .label(label)
                .build();
            
            Map<String, Object> boxMap = box.toMap();
            boxMap.put("id", uniqueId); // Add unique ID for deduplication
            boxes.add(boxMap);
        }
        
        return boxes;
    }
    
    /**
     * Adjust RGBA opacity
     */
    private String adjustOpacity(String rgba, double opacity) {
        if (rgba.startsWith("rgba(")) {
            String[] parts = rgba.substring(5, rgba.length() - 1).split(",");
            if (parts.length >= 3) {
                return String.format("rgba(%s, %s, %s, %.2f)", 
                    parts[0].trim(), parts[1].trim(), parts[2].trim(), 
                    Math.min(1.0, Math.max(0.0, opacity)));
            }
        }
        return rgba;
    }
    
    @Override
    public Map<String, IndicatorMetadata> getVisualizationMetadata(Map<String, Object> params) {
        return new HashMap<>(); // No chart overlays, only boxes
    }
    
    @Override
    public int getMinRequiredCandles(Map<String, Object> params) {
        params = mergeWithDefaults(params);
        return getIntParameter(params, "lookbackPeriod", 200);
    }
    
    /**
     * Helper class for price clustering
     */
    private static class PriceCluster {
        BigDecimal centerPrice;
        List<PricePoint> points = new ArrayList<>();
        int pointCount = 0;
        BigDecimal totalVolume = BigDecimal.ZERO;
        BigDecimal avgRejection = BigDecimal.ZERO;
        Instant firstSeen = null;
        Instant lastSeen = null;
        
        PriceCluster(BigDecimal centerPrice) {
            this.centerPrice = centerPrice;
        }
        
        void addPoint(PricePoint point) {
            points.add(point);
            pointCount++;
            totalVolume = totalVolume.add(point.volume);
            
            // Update average rejection strength
            if (point.rejectionStrength.compareTo(BigDecimal.ZERO) > 0) {
                avgRejection = avgRejection.add(point.rejectionStrength)
                    .divide(BigDecimal.valueOf(pointCount), 4, RoundingMode.HALF_UP);
            }
            
            if (firstSeen == null || point.timestamp.isBefore(firstSeen)) {
                firstSeen = point.timestamp;
            }
            if (lastSeen == null || point.timestamp.isAfter(lastSeen)) {
                lastSeen = point.timestamp;
            }
            
            // Recalculate center as weighted average
            BigDecimal weightedSum = BigDecimal.ZERO;
            BigDecimal totalWeight = BigDecimal.ZERO;
            
            for (PricePoint p : points) {
                BigDecimal weight = p.volume;
                if (p.type.equals("rejection")) {
                    weight = weight.multiply(BigDecimal.valueOf(2)); // Double weight for rejections
                }
                weightedSum = weightedSum.add(p.price.multiply(weight));
                totalWeight = totalWeight.add(weight);
            }
            
            if (totalWeight.compareTo(BigDecimal.ZERO) > 0) {
                centerPrice = weightedSum.divide(totalWeight, 8, RoundingMode.HALF_UP);
            }
        }
    }
}

