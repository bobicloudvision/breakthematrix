package org.cloudvision.trading.bot.replay;

import org.cloudvision.trading.bot.indicators.IndicatorInstanceManager;
import org.cloudvision.trading.model.CandlestickData;
import org.cloudvision.trading.service.CandlestickHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing indicator replay sessions
 * Supports multiple concurrent replay sessions with playback controls
 */
@Service
public class ReplayService {
    
    private final CandlestickHistoryService historyService;
    private final IndicatorInstanceManager indicatorManager;
    
    // Active replay sessions
    private final Map<String, ReplaySession> activeSessions = new ConcurrentHashMap<>();
    
    @Autowired
    public ReplayService(CandlestickHistoryService historyService,
                        IndicatorInstanceManager indicatorManager) {
        this.historyService = historyService;
        this.indicatorManager = indicatorManager;
        
        System.out.println("✅ ReplayService initialized");
    }
    
    /**
     * Create a new replay session
     * 
     * @param config Replay configuration
     * @return Session ID
     * @throws Exception if initialization fails
     */
    public String createSession(ReplayConfig config) throws Exception {
        // Generate session ID
        String sessionId = UUID.randomUUID().toString();
        
        System.out.println("🎬 Creating replay session: " + sessionId);
        System.out.println("   Provider: " + config.getProvider());
        System.out.println("   Symbol: " + config.getSymbol());
        System.out.println("   Interval: " + config.getInterval());
        System.out.println("   Speed: " + config.getSpeed() + "x");
        
        // Load historical candles
        List<CandlestickData> candles;
        
        if (config.getStartTime() != null && config.getEndTime() != null) {
            // Load candles within time range
            candles = historyService.getCandlesticks(
                config.getProvider(),
                config.getSymbol(),
                config.getInterval(),
                config.getStartTime(),
                config.getEndTime()
            );
        } else {
            // Load all available candles
            candles = historyService.getCandlesticks(
                config.getProvider(),
                config.getSymbol(),
                config.getInterval()
            );
        }
        
        if (candles.isEmpty()) {
            // Print debug info
            System.err.println("❌ No historical data found");
            System.err.println("   Looking for: " + config.getProvider() + ":" + 
                             config.getSymbol() + ":" + config.getInterval());
            
            // Print what's available
            Map<String, Integer> stats = historyService.getStorageStats();
            if (stats.isEmpty()) {
                System.err.println("   Storage is completely empty!");
            } else {
                System.err.println("   Available data:");
                stats.forEach((key, count) -> {
                    System.err.println("     - " + key + ": " + count + " candles");
                });
            }
            
            throw new IllegalStateException(
                "No historical data available for " + 
                config.getProvider() + ":" + config.getSymbol() + ":" + config.getInterval() + 
                ". Please check the logs above for available data."
            );
        }
        
        System.out.println("📊 Loaded " + candles.size() + " candles for replay");
        
        // Create replay session
        ReplaySession session = new ReplaySession(
            sessionId,
            config,
            candles,
            indicatorManager
        );
        
        // Initialize indicators
        session.initialize();
        
        // Store session
        activeSessions.put(sessionId, session);
        
        System.out.println("✅ Replay session created: " + sessionId);
        
        return sessionId;
    }
    
    /**
     * Start replay playback
     */
    public void play(String sessionId) {
        ReplaySession session = getSession(sessionId);
        session.play();
    }
    
    /**
     * Pause replay playback
     */
    public void pause(String sessionId) {
        ReplaySession session = getSession(sessionId);
        session.pause();
    }
    
    /**
     * Resume replay playback
     */
    public void resume(String sessionId) {
        ReplaySession session = getSession(sessionId);
        session.resume();
    }
    
    /**
     * Stop replay and cleanup
     */
    public void stop(String sessionId) {
        ReplaySession session = activeSessions.remove(sessionId);
        if (session != null) {
            session.stop();
        }
    }
    
    /**
     * Set playback speed
     */
    public void setSpeed(String sessionId, double speed) {
        ReplaySession session = getSession(sessionId);
        session.setSpeed(speed);
    }
    
    /**
     * Jump to specific candle index
     */
    public void jumpTo(String sessionId, int index) {
        ReplaySession session = getSession(sessionId);
        session.jumpTo(index);
    }
    
    /**
     * Get session by ID
     */
    public ReplaySession getSession(String sessionId) {
        ReplaySession session = activeSessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }
        return session;
    }
    
    /**
     * Check if session exists
     */
    public boolean hasSession(String sessionId) {
        return activeSessions.containsKey(sessionId);
    }
    
    /**
     * Get all active sessions
     */
    public List<ReplaySession> getAllSessions() {
        return new ArrayList<>(activeSessions.values());
    }
    
    /**
     * Get session status
     */
    public Map<String, Object> getSessionStatus(String sessionId) {
        ReplaySession session = getSession(sessionId);
        return session.getStatus();
    }
    
    /**
     * Stop all active sessions
     */
    public void stopAll() {
        System.out.println("🛑 Stopping all replay sessions...");
        for (String sessionId : new ArrayList<>(activeSessions.keySet())) {
            stop(sessionId);
        }
        System.out.println("✅ All replay sessions stopped");
    }
    
    /**
     * Get count of active sessions
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }
    
    /**
     * Get history service for debugging
     */
    public CandlestickHistoryService getHistoryService() {
        return historyService;
    }
}

