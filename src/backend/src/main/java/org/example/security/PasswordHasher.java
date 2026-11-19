package org.example.security;

public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String hashedPassword);

    boolean isHash(String storedPassword);
}
