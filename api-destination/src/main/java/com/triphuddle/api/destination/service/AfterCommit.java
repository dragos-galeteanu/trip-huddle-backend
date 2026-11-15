package com.triphuddle.api.destination.service;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs a send once the current transaction commits, so nothing is announced that was rolled back.
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    /**
     * Run once the current transaction commits, or at once outside one.
     *
     * @param send what to run
     */
    public static void run(Runnable send) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
