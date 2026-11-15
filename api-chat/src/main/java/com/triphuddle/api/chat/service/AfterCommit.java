package com.triphuddle.api.chat.service;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs side effects once the surrounding transaction commits, or at once when there is none.
 */
final class AfterCommit {

    private AfterCommit() {}

    /**
     * Run the action after the current transaction commits; it is dropped if the transaction rolls back.
     *
     * @param action the side effect, such as a socket event or a published message
     */
    static void run(Runnable action) {
        // Pushing an event for a message whose transaction then rolls back would show clients a ghost.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
