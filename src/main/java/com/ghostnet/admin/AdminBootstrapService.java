package com.ghostnet.admin;

import com.ghostnet.network.NetworkInterfaceService;
import com.ghostnet.room.RoomMemberRecord;
import com.ghostnet.room.RoomMemberRole;
import com.ghostnet.room.RoomMembershipRepository;
import com.ghostnet.room.RoomRecord;
import com.ghostnet.room.RoomRepository;
import com.ghostnet.security.PasswordService;
import com.ghostnet.user.UserRepository;
import com.ghostnet.user.UserRecord;
import com.ghostnet.user.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Bootstraps initial system configuration:
 * 1. Default Host Administrator account ('admin' / 'ghostadmin' or custom).
 * 2. Default Public Room ('# General Mesh' with code 'GN-MAIN') so connected clients
 *    can immediately exchange messages without manual configuration.
 * 3. Enrolls existing operatives into the default public room.
 */
@Service
public class AdminBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);

    public static final String DEFAULT_ROOM_CODE = "GN-MAIN";
    public static final String DEFAULT_ROOM_NAME = "General Mesh";

    @Value("${ghostnet.admin.default-username:admin}")
    private String defaultAdminUsername;

    @Value("${ghostnet.admin.default-password:ghostadmin}")
    private String defaultAdminPassword;

    @Value("${server.port:8080}")
    private int serverPort;

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final RoomRepository roomRepository;
    private final RoomMembershipRepository roomMembershipRepository;
    private final NetworkInterfaceService networkInterfaceService;

    public AdminBootstrapService(UserRepository userRepository,
                                 PasswordService passwordService,
                                 RoomRepository roomRepository,
                                 RoomMembershipRepository roomMembershipRepository,
                                 NetworkInterfaceService networkInterfaceService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.roomRepository = roomRepository;
        this.roomMembershipRepository = roomMembershipRepository;
        this.networkInterfaceService = networkInterfaceService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    public void bootstrap() {
        UserRecord adminUser = ensureAdminUser();
        RoomRecord mainRoom = ensureDefaultRoom(adminUser.id());
        enrollAllUsersInDefaultRoom(mainRoom.id(), adminUser.id());
        printStartupBanner();
    }

    private UserRecord ensureAdminUser() {
        Optional<UserRecord> existingAdmin = userRepository.findByUsername(defaultAdminUsername);
        if (existingAdmin.isPresent()) {
            UserRecord admin = existingAdmin.get();
            if (admin.role() != UserRole.ROLE_ADMIN) {
                UserRecord updated = new UserRecord(
                        admin.id(),
                        admin.username(),
                        admin.passwordHash(),
                        admin.displayName(),
                        UserRole.ROLE_ADMIN,
                        admin.bio(),
                        admin.createdAt(),
                        Instant.now()
                );
                userRepository.update(updated);
                return updated;
            }
            return admin;
        }

        // Create initial host admin account
        String adminId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        String passwordHash = passwordService.hashPassword(defaultAdminPassword);

        UserRecord adminUser = new UserRecord(
                adminId,
                defaultAdminUsername,
                passwordHash,
                "GhostNet Host Admin",
                UserRole.ROLE_ADMIN,
                "Primary Host Server Administrator",
                now,
                now
        );
        userRepository.save(adminUser);
        log.info("Initialized default host administrator account: '{}'", defaultAdminUsername);
        return adminUser;
    }

    private RoomRecord ensureDefaultRoom(String adminUserId) {
        Optional<RoomRecord> existingRoom = roomRepository.findByCode(DEFAULT_ROOM_CODE);
        if (existingRoom.isPresent()) {
            return existingRoom.get();
        }

        String roomId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        RoomRecord mainRoom = new RoomRecord(
                roomId,
                DEFAULT_ROOM_CODE,
                DEFAULT_ROOM_NAME,
                "Public broadcast enclave for all connected GhostNet operatives",
                false,
                null,
                100,
                adminUserId,
                now
        );

        roomRepository.save(mainRoom);
        roomMembershipRepository.addMember(new RoomMemberRecord(
                roomId,
                adminUserId,
                RoomMemberRole.OWNER,
                now
        ));
        log.info("Initialized default public room '{}' (Code: {})", DEFAULT_ROOM_NAME, DEFAULT_ROOM_CODE);
        return mainRoom;
    }

    private void enrollAllUsersInDefaultRoom(String defaultRoomId, String adminUserId) {
        List<UserRecord> allUsers = userRepository.findAll();
        for (UserRecord user : allUsers) {
            if (!roomMembershipRepository.isMember(defaultRoomId, user.id())) {
                RoomMemberRole role = user.id().equals(adminUserId) ? RoomMemberRole.OWNER : RoomMemberRole.MEMBER;
                roomMembershipRepository.addMember(new RoomMemberRecord(
                        defaultRoomId,
                        user.id(),
                        role,
                        Instant.now()
                ));
                log.info("Auto-enrolled operative '{}' into #{}", user.username(), DEFAULT_ROOM_NAME);
            }
        }
    }

    private void printStartupBanner() {
        String hostIp = networkInterfaceService.getPrimaryLocalAddress();
        System.out.println();
        System.out.println("================================================================================");
        System.out.println("  GHOSTNET v1.0.0 — PRIVATE OFFLINE-FIRST NETWORK READY");
        System.out.println("================================================================================");
        System.out.println("  Web Client URL:        http://" + hostIp + ":" + serverPort + "/");
        System.out.println("  Host Admin URL:        http://" + hostIp + ":" + serverPort + "/admin.html");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("  DEFAULT HOST ADMIN CREDENTIALS:");
        System.out.println("    Username: " + defaultAdminUsername);
        System.out.println("    Password: " + defaultAdminPassword);
        System.out.println("  (Users with ROLE_ADMIN e.g. 'harsh045' also have full dashboard access)");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("  DEFAULT PUBLIC ROOM:");
        System.out.println("    Enclave:  #" + DEFAULT_ROOM_NAME);
        System.out.println("    Code:     " + DEFAULT_ROOM_CODE + " (All connected operatives auto-join)");
        System.out.println("================================================================================");
        System.out.println();
    }
}
