package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import edu.eci.dosw.campusseguro.identity.identity_service.config.MailConfig;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;

/**
 * Sends the emails with codes (registration RF-MS01-01 and recovery RF-MS01-08) outside the request thread and only
 * after the transaction that stored the code is committed, so a code that was not stored is never sent and the response
 * never waits for the mail server
 *
 * A failure of the mail server or a full queue is only logged, without the email or the code, and never reaches the
 * client
 */
@Component
public class AfterCommitMailer {

    private static final Logger log = LoggerFactory.getLogger(AfterCommitMailer.class);

    private final Executor mailExecutor;

        /**
     * Creates the mailer with the executor of the emails
     *
     * @param mailExecutor executor with which the emails are sent outside the request thread
     */
    public AfterCommitMailer(@Qualifier(MailConfig.MAIL_EXECUTOR) Executor mailExecutor) {
        this.mailExecutor = mailExecutor;
    }

        /**
     * Schedules the sending of an email for after the transaction is committed, if there is no active transaction it is
     * handed to the executor immediately
     *
     * @param description what is sent, for the logs, it must not contain the email or the code
     * @param sending task that sends the email
     * @param correlationId correlation id of the request, it is placed in the logging context of the sending thread
     */
    public void sendAfterCommit(String description, Runnable sending, String correlationId) {
        Runnable delivery = () -> deliver(description, sending, correlationId);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatch(description, delivery);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatch(description, delivery);
            }
        });
    }

        /**
     * Hands the sending to the email executor, if the queue is full the sending is discarded and logged
     *
     * @param description what is sent
     * @param delivery task that sends the email
     */
    private void dispatch(String description, Runnable delivery) {
        try {
            mailExecutor.execute(delivery);
        } catch (RejectedExecutionException e) {
            log.error("Sending of the {} discarded: the mail queue is full", description);
        }
    }

        /**
     * Sends the email, a failure of the mail server is logged without its message because it may contain the email of
     * the recipient
     *
     * @param description what is sent
     * @param sending task that sends the email
     * @param correlationId correlation id of the request
     */
    private void deliver(String description, Runnable sending, String correlationId) {
        MDC.put(CorrelationIds.MDC_KEY, correlationId);
        try {
            sending.run();
            log.info("Sent: {}", description);
        } catch (RuntimeException e) {
            log.error("The {} could not be sent. cause={}", description, rootCauseName(e));
        } finally {
            MDC.remove(CorrelationIds.MDC_KEY);
        }
    }

        /**
     * Gets the class name of the root cause of an error, without its message because it may contain the email of the
     * recipient
     *
     * @param error error from which the root cause is obtained
     * @return the simple name of the class of the root cause
     */
    private static String rootCauseName(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
    }
}
