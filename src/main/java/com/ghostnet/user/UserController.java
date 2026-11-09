package com.ghostnet.user;

import com.ghostnet.security.RequireAuth;
import com.ghostnet.security.SecurityContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for User profile management, search, and presence discovery (Req 024, 118, 119).
 */
@RestController
@RequestMapping("/api/users")
@RequireAuth
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PutMapping("/profile")
    public ResponseEntity<UserDto> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        UserRecord current = SecurityContext.requireUser();
        UserDto updated = userService.updateProfile(current.id(), request);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        UserRecord current = SecurityContext.requireUser();
        userService.changePassword(current.id(), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserDto>> searchUsers(@RequestParam(defaultValue = "") String query) {
        UserRecord current = SecurityContext.requireUser();
        List<UserDto> results = userService.searchUsers(query, current.id());
        return ResponseEntity.ok(results);
    }

    @GetMapping("/all")
    public ResponseEntity<List<UserDto>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/online")
    public ResponseEntity<List<UserDto>> getOnlineUsers() {
        List<UserDto> all = userService.getAllUsers();
        return ResponseEntity.ok(all.stream().filter(UserDto::online).toList());
    }
}
