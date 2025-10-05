package org.cloudvision.trading.bot.replay;

/**
 * Replay session state
 */
public enum ReplayState {
    INITIALIZING("Initializing"),
    READY("Ready"),
    PLAYING("Playing"),
    PAUSED("Paused"),
    STOPPED("Stopped"),
    COMPLETED("Completed"),
    ERROR("Error");
    
    private final String displayName;
    
    ReplayState(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}

