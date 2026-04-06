package com.bekololek.pluginfactory.user;

import com.bekololek.pluginfactory.common.exception.NotFoundException;
import com.bekololek.pluginfactory.subscription.Subscription;
import com.bekololek.pluginfactory.subscription.SubscriptionRepository;
import com.bekololek.pluginfactory.subscription.Tier;
import com.bekololek.pluginfactory.user.dto.UpdateProfileRequest;
import com.bekololek.pluginfactory.user.dto.UsageStatsDto;
import com.bekololek.pluginfactory.user.dto.UserDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserService userService;

    @Test
    void getCurrentUser_success() {
        UUID userId = UUID.randomUUID();

        User user = new User();
        user.setId(userId);
        user.setEmail("test@example.com");
        user.setDisplayName("TestUser");
        user.setDiscordId("123456789");
        user.setStatus(User.UserStatus.ACTIVE);

        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setTier(Tier.FREE);

        UserDto expectedDto = new UserDto(
                userId, "test@example.com", "TestUser", "123456789",
                "ACTIVE", "FREE", Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(subscription));
        when(userMapper.toDto(user, subscription)).thenReturn(expectedDto);

        UserDto result = userService.getCurrentUser(userId);

        assertThat(result.email()).isEqualTo("test@example.com");
        assertThat(result.displayName()).isEqualTo("TestUser");
        assertThat(result.tier()).isEqualTo("FREE");
    }

    @Test
    void getCurrentUser_notFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getCurrentUser(userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void updateProfile_success() {
        UUID userId = UUID.randomUUID();

        User user = new User();
        user.setId(userId);
        user.setEmail("test@example.com");
        user.setDisplayName("OldName");

        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setTier(Tier.FREE);

        UpdateProfileRequest request = new UpdateProfileRequest("NewName");

        UserDto expectedDto = new UserDto(
                userId, "test@example.com", "NewName", null,
                "ACTIVE", "FREE", Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(subscription));
        when(userMapper.toDto(any(User.class), any(Subscription.class))).thenReturn(expectedDto);

        UserDto result = userService.updateProfile(userId, request);

        assertThat(result.displayName()).isEqualTo("NewName");
        verify(userRepository).save(user);
    }

    @Test
    void getUsageStats_success() {
        UUID userId = UUID.randomUUID();

        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setTier(Tier.FREE);
        subscription.setBuildsUsedThisPeriod(0);

        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(subscription));

        UsageStatsDto result = userService.getUsageStats(userId);

        assertThat(result.buildsUsed()).isEqualTo(0);
        assertThat(result.buildsLimit()).isEqualTo(1);
        assertThat(result.tier()).isEqualTo("FREE");
    }
}
