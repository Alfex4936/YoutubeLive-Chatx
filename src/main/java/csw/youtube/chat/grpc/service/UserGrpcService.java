package csw.youtube.chat.grpc.service;

import csw.youtube.chat.grpc.user.UserServiceGrpc;
import csw.youtube.chat.grpc.user.UserServiceProto.*;
import csw.youtube.chat.user.model.User;
import csw.youtube.chat.user.service.UserService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.stream.Collectors;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

    private final UserService userService;

    @Override
    public void getUserProfile(GetUserProfileRequest request, StreamObserver<GetUserProfileResponse> responseObserver) {
        try {
            Long userId = request.getUserId();
            log.info("gRPC: Getting user profile for user ID: {}", userId);

            User user = userService.findById(userId);

            if (user == null) {
                responseObserver.onError(new RuntimeException("User not found with ID: " + userId));
                return;
            }

            GetUserProfileResponse.Builder responseBuilder = GetUserProfileResponse.newBuilder()
                    .setId(user.getId())
                    .setUsername(user.getUsername())
                    .setEmail(user.getEmail())
                    .setRole(user.getRole().getClass().getSimpleName())
                    .addAllPermissions(
                            user.getRole().getPermissions().stream()
                                    .map(permission -> permission.getPermission())
                                    .collect(Collectors.toList())
                    );

            if (user.getCreatedAt() != null) {
                responseBuilder.setCreatedAt(user.getCreatedAt().getEpochSecond());
            }

            if (user.getLastLogin() != null) {
                responseBuilder.setLastLogin(user.getLastLogin().getEpochSecond());
            }

            // Add role description based on role type
            String roleDescription = switch (user.getRole().getClass().getSimpleName()) {
                case "AdminRole" -> "System Administrator with full privileges";
                case "ManagerRole" -> "Team Manager with resource management access";
                case "UserRole" -> "Standard application user";
                default -> "Unknown role";
            };
            responseBuilder.setRoleDescription(roleDescription);

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("gRPC: Error getting user profile", e);
            responseObserver.onError(e);
        }
    }
}