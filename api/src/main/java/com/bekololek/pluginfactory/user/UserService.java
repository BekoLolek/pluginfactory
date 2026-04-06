package com.bekololek.pluginfactory.user;

import com.bekololek.pluginfactory.common.exception.NotFoundException;
import com.bekololek.pluginfactory.subscription.Subscription;
import com.bekololek.pluginfactory.subscription.SubscriptionRepository;
import com.bekololek.pluginfactory.user.dto.UpdateProfileRequest;
import com.bekololek.pluginfactory.user.dto.UsageStatsDto;
import com.bekololek.pluginfactory.user.dto.UserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public UserDto getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        Subscription subscription = subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Subscription not found"));
        return userMapper.toDto(user, subscription);
    }

    @Transactional
    public UserDto updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        user.setDisplayName(request.displayName());
        user = userRepository.save(user);

        Subscription subscription = subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Subscription not found"));
        return userMapper.toDto(user, subscription);
    }

    @Transactional(readOnly = true)
    public UsageStatsDto getUsageStats(UUID userId) {
        Subscription subscription = subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Subscription not found"));
        return new UsageStatsDto(
                subscription.getBuildsUsedThisPeriod(),
                subscription.getTier().getMaxBuilds(),
                subscription.getTier().name()
        );
    }
}
