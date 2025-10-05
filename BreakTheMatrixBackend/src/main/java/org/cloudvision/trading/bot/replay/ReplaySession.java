package org.cloudvision.trading.bot.replay;

import org.cloudvision.trading.bot.indicators.IndicatorInstanceManager;
import org.cloudvision.trading.model.CandlestickData;
import org.cloudvision.trading.service.CandlestickHistoryService;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Represents a replay session that plays historical candles with indicators
 * Supports playback controls (play, pause, stop, speed adjustment)
 */
public class ReplaySession implements Runnable {
    
    private final String sessionId;
    private final ReplayConfig config;
    private final List<CandlestickData> candles;
    private final IndicatorInstanceManager indicatorManager;
    
    // Playback state
    private volatile ReplayState state;
    private volatile double speed;
    private volatile int currentIndex;
    
    // Indicator instances created for this replay
    private final List<String> indicatorInstanceKeys;
    
    // Event listeners
    private final List<BiConsumer<ReplaySession, ReplayEvent>> eventListeners;
    
    // Thread management
    private Thread playbackThread;
    private volatile boolean shouldStop;
    
    // Metrics
    private final Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    
    public ReplaySession(String sessionId,
                        ReplayConfig config,
                        List<CandlestickData> candles,
                        IndicatorInstanceManager indicatorManager) {
        this.sessionId = sessionId;
        this.config = config;
        this.candles = new ArrayList<>(candles); // Copy to prevent external modifications
        this.indicatorManager = indicatorManager;
        this.state = ReplayState.INITIALIZING;
        this.speed = config.getSpeed();
        this.currentIndex = 0;
        this.indicatorInstanceKeys = new ArrayList<>();
        this.eventListeners = new ArrayList<>();
        this.createdAt = Instant.now();
        this.shouldStop = false;
    }
    
    /**
     * Initialize indicators for this replay session
     * Automatically finds and attaches to existing indicators for the context
     */
    public void initialize() throws Exception {
        System.out.println("🎬 Initializing replay session: " + sessionId);
        System.out.println("   Candles: " + candles.size());
        System.out.println("   Context: " + config.getProvider() + ":" + 
                         config.getSymbol() + ":" + config.getInterval());
        
        // Automatically find and attach to existing indicators for this context
        List<IndicatorInstanceManager.IndicatorInstance> existingInstances = 
            indicatorManager.getInstancesForContext(
                config.getProvider(),
                config.getSymbol(),
                config.getInterval()
            );
        
        if (!existingInstances.isEmpty()) {
            System.out.println("   Found " + existingInstances.size() + " existing indicator(s)");
            
            for (IndicatorInstanceManager.IndicatorInstance instance : existingInstances) {
                indicatorInstanceKeys.add(instance.getInstanceKey());
                System.out.println("✅ Attached to indicator: " + instance.getIndicatorId() + 
                                 " (" + instance.getInstanceKey() + ")");
            }
        } else {
            System.out.println("⚠️ No existing indicators found for this context");
            System.out.println("   Replay will show candles only (no indicators)");
        }
        
        this.state = ReplayState.READY;
        System.out.println("✅ Replay session initialized: " + sessionId);
    }
    
    /**
     * Start playback
     */
    public void play() {
        if (state == ReplayState.COMPLETED) {
            // Restart from beginning
            currentIndex = 0;
        }
        
        if (state == ReplayState.PLAYING) {
            System.out.println("⚠️ Replay already playing");
            return;
        }
        
        this.state = ReplayState.PLAYING;
        this.startedAt = Instant.now();
        this.shouldStop = false;
        
        // Start playback thread
        playbackThread = new Thread(this, "ReplaySession-" + sessionId);
        playbackThread.start();
        
        System.out.println("▶️ Replay started: " + sessionId);
    }
    
    /**
     * Pause playback
     */
    public void pause() {
        if (state != ReplayState.PLAYING) {
            return;
        }
        
        this.state = ReplayState.PAUSED;
        System.out.println("⏸️ Replay paused: " + sessionId);
    }
    
    /**
     * Resume playback
     */
    public void resume() {
        if (state != ReplayState.PAUSED) {
            return;
        }
        
        this.state = ReplayState.PLAYING;
        System.out.println("▶️ Replay resumed: " + sessionId);
    }
    
    /**
     * Stop playback and cleanup
     */
    public void stop() {
        this.shouldStop = true;
        this.state = ReplayState.STOPPED;
        
        if (playbackThread != null) {
            playbackThread.interrupt();
        }
        
        cleanup();
        System.out.println("⏹️ Replay stopped: " + sessionId);
    }
    
    /**
     * Set playback speed
     */
    public void setSpeed(double speed) {
        if (speed <= 0) {
            throw new IllegalArgumentException("Speed must be positive");
        }
        this.speed = speed;
        System.out.println("⚡ Replay speed changed to " + speed + "x: " + sessionId);
    }
    
    /**
     * Jump to specific candle index
     */
    public void jumpTo(int index) {
        if (index < 0 || index >= candles.size()) {
            throw new IllegalArgumentException("Index out of bounds: " + index);
        }
        this.currentIndex = index;
        System.out.println("⏩ Jumped to index " + index + ": " + sessionId);
    }
    
    /**
     * Add event listener
     */
    public void addEventListener(BiConsumer<ReplaySession, ReplayEvent> listener) {
        this.eventListeners.add(listener);
    }
    
    /**
     * Main playback loop
     */
    @Override
    public void run() {
        try {
            while (currentIndex < candles.size() && !shouldStop) {
                // Check if paused
                while (state == ReplayState.PAUSED && !shouldStop) {
                    Thread.sleep(100);
                }
                
                if (shouldStop || state == ReplayState.STOPPED) {
                    break;
                }
                
                // Get current candle
                CandlestickData candle = candles.get(currentIndex);
                
                // Update indicators
                Map<String, IndicatorInstanceManager.IndicatorResult> indicatorResults = new HashMap<>();
                for (String instanceKey : indicatorInstanceKeys) {
                    IndicatorInstanceManager.IndicatorResult result = 
                        indicatorManager.updateWithCandle(instanceKey, candle);
                    
                    if (result != null) {
                        indicatorResults.put(instanceKey, result);
                    }
                }
                
                // Create event
                ReplayEvent event = new ReplayEvent(
                    sessionId,
                    currentIndex,
                    candles.size(),
                    candle,
                    indicatorResults,
                    state,
                    speed
                );
                
                // Notify listeners
                notifyListeners(event);
                
                // Move to next candle
                currentIndex++;
                
                // Calculate delay based on speed
                long delayMs = calculateDelay(speed);
                if (delayMs > 0) {
                    Thread.sleep(delayMs);
                }
            }
            
            // Playback completed
            if (currentIndex >= candles.size()) {
                this.state = ReplayState.COMPLETED;
                this.completedAt = Instant.now();
                System.out.println("✅ Replay completed: " + sessionId);
                
                // Notify listeners of completion
                notifyListeners(new ReplayEvent(
                    sessionId,
                    currentIndex,
                    candles.size(),
                    null,
                    Map.of(),
                    ReplayState.COMPLETED,
                    speed
                ));
            }
            
        } catch (InterruptedException e) {
            System.out.println("⏹️ Replay interrupted: " + sessionId);
            this.state = ReplayState.STOPPED;
        } catch (Exception e) {
            System.err.println("❌ Error during replay: " + e.getMessage());
            e.printStackTrace();
            this.state = ReplayState.ERROR;
        }
    }
    
    /**
     * Calculate delay between candles based on speed
     */
    private long calculateDelay(double speed) {
        // Base delay: 100ms per candle at 1x speed
        return (long) (100 / speed);
    }
    
    /**
     * Notify all event listeners
     */
    private void notifyListeners(ReplayEvent event) {
        for (BiConsumer<ReplaySession, ReplayEvent> listener : eventListeners) {
            try {
                listener.accept(this, event);
            } catch (Exception e) {
                System.err.println("❌ Error in replay event listener: " + e.getMessage());
            }
        }
    }
    
    /**
     * Cleanup resources
     */
    private void cleanup() {
        // Don't deactivate indicators - they're existing instances used by live system
        System.out.println("ℹ️ Cleanup: Keeping indicators active (they're used by live system)");
        
        indicatorInstanceKeys.clear();
        eventListeners.clear();
    }
    
    // Getters
    public String getSessionId() { return sessionId; }
    public ReplayState getState() { return state; }
    public double getSpeed() { return speed; }
    public int getCurrentIndex() { return currentIndex; }
    public int getTotalCandles() { return candles.size(); }
    public double getProgress() { 
        return candles.isEmpty() ? 0.0 : (currentIndex * 100.0 / candles.size()); 
    }
    public ReplayConfig getConfig() { return config; }
    public List<String> getIndicatorInstanceKeys() { return List.copyOf(indicatorInstanceKeys); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    
    /**
     * Get current candle (if available)
     */
    public CandlestickData getCurrentCandle() {
        if (currentIndex >= 0 && currentIndex < candles.size()) {
            return candles.get(currentIndex);
        }
        return null;
    }
    
    /**
     * Get session status summary
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("sessionId", sessionId);
        status.put("state", state);
        status.put("currentIndex", currentIndex);
        status.put("totalCandles", candles.size());
        status.put("progress", getProgress());
        status.put("speed", speed);
        status.put("provider", config.getProvider());
        status.put("symbol", config.getSymbol());
        status.put("interval", config.getInterval());
        status.put("indicatorCount", indicatorInstanceKeys.size());
        status.put("createdAt", createdAt);
        
        if (startedAt != null) {
            status.put("startedAt", startedAt);
        }
        if (completedAt != null) {
            status.put("completedAt", completedAt);
        }
        
        CandlestickData currentCandle = getCurrentCandle();
        if (currentCandle != null) {
            status.put("currentTime", currentCandle.getOpenTime());
        }
        
        return status;
    }
}

