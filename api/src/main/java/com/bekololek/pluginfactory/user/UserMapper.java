package com.bekololek.pluginfactory.user;

import com.bekololek.pluginfactory.subscription.Subscription;
import com.bekololek.pluginfactory.user.dto.UserDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(source = "user.id", target = "id")
    @Mapping(source = "user.email", target = "email")
    @Mapping(source = "user.displayName", target = "displayName")
    @Mapping(source = "user.discordId", target = "discordId")
    @Mapping(source = "user.status", target = "status")
    @Mapping(source = "user.createdAt", target = "createdAt")
    @Mapping(target = "tier", expression = "java(subscription.getTier().name())")
    UserDto toDto(User user, Subscription subscription);
}
