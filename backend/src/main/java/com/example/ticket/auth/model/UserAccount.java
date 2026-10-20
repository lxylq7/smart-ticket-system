package com.example.ticket.auth.model;

/** Internal database model. Never returned by an API. */
public record UserAccount(Long id, String username, String passwordHash,
                          String displayName, String status) {
}
