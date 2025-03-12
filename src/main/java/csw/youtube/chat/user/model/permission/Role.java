package csw.youtube.chat.user.model.permission;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public sealed interface Role permits AdminRole, ManagerRole, UserRole {

    Role ADMIN = new AdminRole();
    Role MANAGER = new ManagerRole();
    Role USER = new UserRole();

    String name();
    Set<Permission> permissions();

    default List<SimpleGrantedAuthority> getAuthorities() {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + name()));
        permissions().stream()
                .map(p -> new SimpleGrantedAuthority(p.getPermission()))
                .forEach(authorities::add);
        return authorities;
    }
}

