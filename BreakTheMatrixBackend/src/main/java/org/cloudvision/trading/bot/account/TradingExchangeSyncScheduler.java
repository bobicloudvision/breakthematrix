package org.cloudvision.trading.bot.account;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically synchronizes account state from the exchange into PositionManager.
 */
@Component
public class TradingExchangeSyncScheduler {

    private final AccountManager accountManager;

    public TradingExchangeSyncScheduler(AccountManager accountManager) {
        this.accountManager = accountManager;
    }

    // Run frequent sync for low-latency reconciliation. Tune as needed.
    @Scheduled(fixedDelayString = "${exchange.sync.fixedDelayMs:1500}")
    public void syncActiveAccount() {
        TradingAccount active = accountManager.getActiveAccount();
        if (active instanceof AlphadexTradingAccount alphadex) {
            alphadex.syncFromExchange();
        }
    }
}


