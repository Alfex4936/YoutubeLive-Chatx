package csw.youtube.chat.user.model.permission;

import java.util.Set;

public record UserRole() implements Role {
    @Override
    public String name() {
        return "USER";
    }

    @Override
    public Set<Permission> permissions() {
        return Set.of();
    }
}
