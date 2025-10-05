package org.cloudvision.trading.bot.controller;

import org.cloudvision.trading.bot.replay.ReplayConfig;
import org.cloudvision.trading.bot.replay.ReplayService;
import org.cloudvision.trading.bot.replay.ReplaySession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for replay functionality
 * Provides endpoints for creating and controlling replay sessions
 */
@RestController
@RequestMapping("/api/replay")
@CrossOrigin(origins = "*")
public class ReplayController {
    
    private final ReplayService replayService;
    
    @Autowired
    public ReplayController(ReplayService replayService) {
        this.replayService = replayService;
    }
    
    /**
     * Create a new replay session
     * 
     * POST /api/replay/create
     * Body: ReplayConfig
     * Returns: { "sessionId": "...", "status": {...} }
     */
    @PostMapping("/create")
    public ResponseEntity<?> createSession(@RequestBody ReplayConfig config) {
        try {
            String sessionId = replayService.createSession(config);
            Map<String, Object> status = replayService.getSessionStatus(sessionId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("sessionId", sessionId);
            response.put("status", status);
            response.put("message", "Replay session created successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to create replay session: " + e.getMessage()));
        }
    }
    
    /**
     * Start replay playback
     * 
     * POST /api/replay/{sessionId}/play
     */
    @PostMapping("/{sessionId}/play")
    public ResponseEntity<?> play(@PathVariable String sessionId) {
        try {
            replayService.play(sessionId);
            return ResponseEntity.ok(Map.of(
                "message", "Playback started",
                "sessionId", sessionId
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Pause replay playback
     * 
     * POST /api/replay/{sessionId}/pause
     */
    @PostMapping("/{sessionId}/pause")
    public ResponseEntity<?> pause(@PathVariable String sessionId) {
        try {
            replayService.pause(sessionId);
            return ResponseEntity.ok(Map.of(
                "message", "Playback paused",
                "sessionId", sessionId
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Resume replay playback
     * 
     * POST /api/replay/{sessionId}/resume
     */
    @PostMapping("/{sessionId}/resume")
    public ResponseEntity<?> resume(@PathVariable String sessionId) {
        try {
            replayService.resume(sessionId);
            return ResponseEntity.ok(Map.of(
                "message", "Playback resumed",
                "sessionId", sessionId
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Stop replay and cleanup
     * 
     * DELETE /api/replay/{sessionId}
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<?> stop(@PathVariable String sessionId) {
        try {
            replayService.stop(sessionId);
            return ResponseEntity.ok(Map.of(
                "message", "Replay stopped",
                "sessionId", sessionId
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Set playback speed
     * 
     * POST /api/replay/{sessionId}/speed
     * Body: { "speed": 2.0 }
     */
    @PostMapping("/{sessionId}/speed")
    public ResponseEntity<?> setSpeed(@PathVariable String sessionId,
                                     @RequestBody Map<String, Object> body) {
        try {
            Double speed = ((Number) body.get("speed")).doubleValue();
            replayService.setSpeed(sessionId, speed);
            
            return ResponseEntity.ok(Map.of(
                "message", "Speed changed to " + speed + "x",
                "sessionId", sessionId,
                "speed", speed
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Invalid speed value"));
        }
    }
    
    /**
     * Jump to specific candle index
     * 
     * POST /api/replay/{sessionId}/jump
     * Body: { "index": 100 }
     */
    @PostMapping("/{sessionId}/jump")
    public ResponseEntity<?> jumpTo(@PathVariable String sessionId,
                                    @RequestBody Map<String, Object> body) {
        try {
            Integer index = ((Number) body.get("index")).intValue();
            replayService.jumpTo(sessionId, index);
            
            return ResponseEntity.ok(Map.of(
                "message", "Jumped to index " + index,
                "sessionId", sessionId,
                "index", index
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Invalid index value"));
        }
    }
    
    /**
     * Get session status
     * 
     * GET /api/replay/{sessionId}/status
     */
    @GetMapping("/{sessionId}/status")
    public ResponseEntity<?> getStatus(@PathVariable String sessionId) {
        try {
            Map<String, Object> status = replayService.getSessionStatus(sessionId);
            return ResponseEntity.ok(status);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Get all active sessions
     * 
     * GET /api/replay/sessions
     */
    @GetMapping("/sessions")
    public ResponseEntity<?> getAllSessions() {
        try {
            List<ReplaySession> sessions = replayService.getAllSessions();
            
            List<Map<String, Object>> sessionList = sessions.stream()
                .map(session -> session.getStatus())
                .collect(Collectors.toList());
            
            Map<String, Object> response = new HashMap<>();
            response.put("totalSessions", sessions.size());
            response.put("sessions", sessionList);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Stop all active sessions
     * 
     * POST /api/replay/stop-all
     */
    @PostMapping("/stop-all")
    public ResponseEntity<?> stopAll() {
        try {
            int count = replayService.getActiveSessionCount();
            replayService.stopAll();
            
            return ResponseEntity.ok(Map.of(
                "message", "Stopped " + count + " replay session(s)",
                "stoppedCount", count
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Check available historical data with time ranges
     * 
     * GET /api/replay/available-data
     */
    @GetMapping("/available-data")
    public ResponseEntity<?> getAvailableData() {
        try {
            org.cloudvision.trading.service.CandlestickHistoryService historyService = 
                replayService.getHistoryService();
            
            Map<String, Integer> stats = historyService.getStorageStats();
            
            List<Map<String, Object>> dataList = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : stats.entrySet()) {
                String key = entry.getKey();
                String[] parts = key.split("_");
                
                if (parts.length >= 3) {
                    String provider = parts[0];
                    String symbol = parts[1];
                    String interval = parts[2];
                    
                    // Get all candles to find time range
                    List<org.cloudvision.trading.model.CandlestickData> candles = 
                        historyService.getCandlesticks(provider, symbol, interval);
                    
                    Map<String, Object> info = new HashMap<>();
                    info.put("key", key);
                    info.put("provider", provider);
                    info.put("symbol", symbol);
                    info.put("interval", interval);
                    info.put("candleCount", entry.getValue());
                    
                    // Add time range if candles exist
                    if (!candles.isEmpty()) {
                        org.cloudvision.trading.model.CandlestickData firstCandle = candles.get(0);
                        org.cloudvision.trading.model.CandlestickData lastCandle = candles.get(candles.size() - 1);
                        
                        info.put("startTime", firstCandle.getOpenTime().toString());
                        info.put("endTime", lastCandle.getCloseTime().toString());
                        info.put("startTimeMs", firstCandle.getOpenTime().toEpochMilli());
                        info.put("endTimeMs", lastCandle.getCloseTime().toEpochMilli());
                        
                        // Calculate duration
                        long durationMs = lastCandle.getCloseTime().toEpochMilli() - 
                                         firstCandle.getOpenTime().toEpochMilli();
                        long durationHours = durationMs / (1000 * 60 * 60);
                        long durationDays = durationHours / 24;
                        
                        if (durationDays > 0) {
                            info.put("duration", durationDays + " days");
                        } else if (durationHours > 0) {
                            info.put("duration", durationHours + " hours");
                        } else {
                            long durationMinutes = durationMs / (1000 * 60);
                            info.put("duration", durationMinutes + " minutes");
                        }
                    }
                    
                    dataList.add(info);
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("totalDataSets", dataList.size());
            response.put("data", dataList);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }
    
}

