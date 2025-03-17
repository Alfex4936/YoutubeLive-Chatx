package csw.youtube.chat.user.model.permission;

import java.util.Set;

public record ManagerRole() implements Role {
    @Override
    public String name() {
        return "MANAGER";
    }

    @Override
    public Set<Permission> permissions() {
        return Set.of(
                Permission.MANAGER_READ,
                Permission.MANAGER_UPDATE,
                Permission.MANAGER_CREATE,
                Permission.MANAGER_DELETE
        );
    }
}
