package org.cloudvision.trading.bot.replay;

import org.cloudvision.trading.bot.indicators.IndicatorInstanceManager;
import org.cloudvision.trading.model.CandlestickData;

import java.util.Map;

/**
 * Event emitted during replay playback
 * Contains candle data and indicator results
 */
public class ReplayEvent {
    
    private final String sessionId;
    private final int currentIndex;
    private final int totalCandles;
    private final CandlestickData candle;
    private final Map<String, IndicatorInstanceManager.IndicatorResult> indicatorResults;
    private final ReplayState state;
    private final double speed;
    
    public ReplayEvent(String sessionId,
                      int currentIndex,
                      int totalCandles,
                      CandlestickData candle,
                      Map<String, IndicatorInstanceManager.IndicatorResult> indicatorResults,
                      ReplayState state,
                      double speed) {
        this.sessionId = sessionId;
        this.currentIndex = currentIndex;
        this.totalCandles = totalCandles;
        this.candle = candle;
        this.indicatorResults = indicatorResults;
        this.state = state;
        this.speed = speed;
    }
    
    // Getters
    public String getSessionId() { return sessionId; }
    public int getCurrentIndex() { return currentIndex; }
    public int getTotalCandles() { return totalCandles; }
    public CandlestickData getCandle() { return candle; }
    public Map<String, IndicatorInstanceManager.IndicatorResult> getIndicatorResults() { 
        return indicatorResults; 
    }
    public ReplayState getState() { return state; }
    public double getSpeed() { return speed; }
    
    public double getProgress() {
        return totalCandles > 0 ? (currentIndex * 100.0 / totalCandles) : 0.0;
    }
}

