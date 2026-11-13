package org.example.entities;

import jakarta.persistence.*;

import java.time.Instant;

/** Freundschaft zwischen zwei Spielern. Erst eine Anfrage (PENDING), nach dem Annehmen ACCEPTED. */
@Entity
@Table(name = "friendship", uniqueConstraints = @UniqueConstraint(columnNames = {"requester_id", "addressee_id"}))
public class Friendship {

    public enum Status { PENDING, ACCEPTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(optional = false)
    @JoinColumn(name = "addressee_id", nullable = false)
    private User addressee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Friendship() {}

    public Friendship(User requester, User addressee) {
        this.requester = requester;
        this.addressee = addressee;
    }

    public Long getId() { return id; }
    public User getRequester() { return requester; }
    public User getAddressee() { return addressee; }
    public Status getStatus() { return status; }
    public void accept() { this.status = Status.ACCEPTED; }
    public Instant getCreatedAt() { return createdAt; }

    /** Der andere aus Sicht von {@code username} */
    public User other(String username) {
        return requester.getUsername().equals(username) ? addressee : requester;
    }
}
