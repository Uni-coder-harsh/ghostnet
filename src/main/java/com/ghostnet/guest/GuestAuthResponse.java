package com.ghostnet.guest;

import com.ghostnet.user.UserDto;

import java.time.Instant;

public record GuestAuthResponse(
        String token,
        String deviceCredential,
        UserDto user,
        Instant expiresAt
) {}
