package com.bekololek.pluginfactory.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void saveAndFindByEmail() {
        User user = new User();
        user.setEmail("test@example.com");
        user.setDisplayName("Test User");
        user.setAuthProvider("discord");
        user.setDiscordId("123456789");

        userRepository.save(user);

        Optional<User> found = userRepository.findByEmail("test@example.com");
        assertThat(found).isPresent();
        assertThat(found.get().getDisplayName()).isEqualTo("Test User");
        assertThat(found.get().getAuthProvider()).isEqualTo("discord");
        assertThat(found.get().getStatus()).isEqualTo(User.UserStatus.ACTIVE);
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getUpdatedAt()).isNotNull();
    }

    @Test
    void findByDiscordId() {
        User user = new User();
        user.setEmail("discord@example.com");
        user.setDisplayName("Discord User");
        user.setAuthProvider("discord");
        user.setDiscordId("987654321");

        userRepository.save(user);

        Optional<User> found = userRepository.findByDiscordId("987654321");
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("discord@example.com");
    }

    @Test
    void findByDiscordIdNotFound() {
        Optional<User> found = userRepository.findByDiscordId("nonexistent");
        assertThat(found).isEmpty();
    }
}
