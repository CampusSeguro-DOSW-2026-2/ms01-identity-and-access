package edu.eci.dosw.campusseguro.identity.identity_service.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "codigo_restablecer_contrasena")
public class PasswordResetCodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private UserEntity user;

    @Column(name = "codigo_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "vence_en", nullable = false)
    private Instant expiresAt;

    @Column(name = "usado_en")
    private Instant usedAt;

    @Column(name = "intentos", nullable = false)
    private int attempts;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UUID getId(){ return this.id; }

    public UserEntity getUser(){ return this.user; }
    public void setUser(UserEntity user){ this.user = user; }

    public String getCodeHash(){ return this.codeHash; }
    public void setCodeHash(String codeHash){ this.codeHash = codeHash; }

    public Instant getExpiresAt(){ return this.expiresAt; }
    public void setExpiresAt(Instant expiresAt){ this.expiresAt = expiresAt; }

    public Instant getUsedAt(){ return this.usedAt; }
    public void setUsedAt(Instant usedAt){ this.usedAt = usedAt; }

    public int getAttempts(){ return this.attempts; }
    public void setAttempts(int attempts){ this.attempts = attempts; }

    public Instant getCreatedAt(){ return this.createdAt; }
}