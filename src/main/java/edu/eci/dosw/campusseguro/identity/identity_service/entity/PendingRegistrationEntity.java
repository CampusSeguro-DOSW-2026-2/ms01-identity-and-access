package edu.eci.dosw.campusseguro.identity.identity_service.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Registration that waits for the verification of its email (table pending_registrations of the data model,
 * RF-MS01-01), the user is only created in users when the code sent to the email is confirmed
 *
 * The email is the key: a new registration attempt with the same email overwrites the previous one. The password, the
 * code and the registration token are never stored in clear text, only their hashes. The token is given only to whoever
 * made the registration and is required to confirm it, so an overwrite by someone else cannot be confirmed with the
 * code that the owner of the email receives. The rows are written with the native queries of the
 * repository, so that the overwrite, the minimum wait and the daily limit of codes are a single atomic statement
 */
@Entity
@Table(name = "pending_registrations")
public class PendingRegistrationEntity {

    @Id
    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @Column(name = "first_name", nullable = false, length = 30)
    private String firstName;

    @Column(name = "second_name", length = 30)
    private String secondName;

    @Column(name = "first_lastname", nullable = false, length = 30)
    private String firstLastName;

    @Column(name = "second_lastname", length = 30)
    private String secondLastName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "request_token_hash", nullable = false, length = 64)
    private String requestTokenHash;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "code_sent_at", nullable = false)
    private Instant codeSentAt;

    @Column(name = "codes_sent", nullable = false)
    private int codesSent;

    @Column(name = "window_started_at", nullable = false)
    private Instant windowStartedAt;

        /**
     * Returns the email that is being verified, which is also the key of the registration
     *
     * @return the email in lowercase
     */
    public String getEmail() {
        return this.email;
    }

        /**
     * Sets the email that is being verified
     *
     * @param email email in lowercase
     */
    public void setEmail(String email) {
        this.email = email;
    }

        /**
     * Returns the first name
     *
     * @return the first name
     */
    public String getFirstName() {
        return this.firstName;
    }

        /**
     * Sets the first name
     *
     * @param name first name
     */
    public void setFirstName(String name) {
        this.firstName = name;
    }

        /**
     * Returns the second name
     *
     * @return the second name, null if it was not sent
     */
    public String getSecondName() {
        return this.secondName;
    }

        /**
     * Sets the second name
     *
     * @param name second name, may be null
     */
    public void setSecondName(String name) {
        this.secondName = name;
    }

        /**
     * Returns the first lastname
     *
     * @return the first lastname
     */
    public String getFirstLastName() {
        return this.firstLastName;
    }

        /**
     * Sets the first lastname
     *
     * @param lastName first lastname
     */
    public void setFirstLastName(String lastName) {
        this.firstLastName = lastName;
    }

        /**
     * Returns the second lastname
     *
     * @return the second lastname, null if it was not sent
     */
    public String getSecondLastName() {
        return this.secondLastName;
    }

        /**
     * Sets the second lastname
     *
     * @param lastName second lastname, may be null
     */
    public void setSecondLastName(String lastName) {
        this.secondLastName = lastName;
    }

        /**
     * Returns the hash of the password chosen in the registration
     *
     * @return the hash of the password
     */
    public String getPasswordHash() {
        return this.passwordHash;
    }

        /**
     * Sets the hash of the password chosen in the registration
     *
     * @param passwordHash hash of the password
     */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

        /**
     * Returns the hash of the last code sent to the email
     *
     * @return the hash of the code
     */
    public String getCodeHash() {
        return this.codeHash;
    }

        /**
     * Sets the hash of the last code sent to the email
     *
     * @param codeHash hash of the code
     */
    public void setCodeHash(String codeHash) {
        this.codeHash = codeHash;
    }

        /**
     * Returns the SHA-256 hash, in hexadecimal, of the token given to whoever made the registration
     *
     * @return the hash of the registration token
     */
    public String getRequestTokenHash() {
        return this.requestTokenHash;
    }

        /**
     * Sets the SHA-256 hash, in hexadecimal, of the token given to whoever made the registration
     *
     * @param requestTokenHash hash of the registration token
     */
    public void setRequestTokenHash(String requestTokenHash) {
        this.requestTokenHash = requestTokenHash;
    }

        /**
     * Returns the failed attempts of the current code
     *
     * @return the failed attempts
     */
    public int getAttempts() {
        return this.attempts;
    }

        /**
     * Sets the failed attempts of the current code
     *
     * @param attempts failed attempts
     */
    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

        /**
     * Returns the instant at which the current code stops being valid
     *
     * @return the expiration of the code
     */
    public Instant getExpiresAt() {
        return this.expiresAt;
    }

        /**
     * Sets the instant at which the current code stops being valid, it is moved to the present to invalidate the code
     *
     * @param expiresAt expiration of the code
     */
    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

        /**
     * Returns the instant at which the last code was sent, from which the minimum wait is counted
     *
     * @return the instant of the last sending
     */
    public Instant getCodeSentAt() {
        return this.codeSentAt;
    }

        /**
     * Sets the instant at which the last code was sent
     *
     * @param codeSentAt instant of the last sending
     */
    public void setCodeSentAt(Instant codeSentAt) {
        this.codeSentAt = codeSentAt;
    }

        /**
     * Returns the codes sent to the email in the current window of one day
     *
     * @return the codes sent in the window
     */
    public int getCodesSent() {
        return this.codesSent;
    }

        /**
     * Sets the codes sent to the email in the current window of one day
     *
     * @param codesSent codes sent in the window
     */
    public void setCodesSent(int codesSent) {
        this.codesSent = codesSent;
    }

        /**
     * Returns the instant at which the current window of one day of the daily limit started
     *
     * @return the start of the window
     */
    public Instant getWindowStartedAt() {
        return this.windowStartedAt;
    }

        /**
     * Sets the instant at which the current window of one day of the daily limit started
     *
     * @param windowStartedAt start of the window
     */
    public void setWindowStartedAt(Instant windowStartedAt) {
        this.windowStartedAt = windowStartedAt;
    }

        /**
     * Tells whether the current code can still be verified
     *
     * @param now current instant
     * @return true if the code has not expired
     */
    public boolean isCodeActiveAt(Instant now) {
        return this.expiresAt.isAfter(now);
    }
}
