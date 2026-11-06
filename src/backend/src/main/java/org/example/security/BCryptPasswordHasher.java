package org.example.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String hashedPassword) {
        return hashedPassword != null && isHash(hashedPassword) && encoder.matches(rawPassword, hashedPassword);
    }

    @Override
    public boolean isHash(String storedPassword) {
        return storedPassword != null && storedPassword.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$");
    }
}
