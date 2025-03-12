package csw.youtube.chat.user.model.permission;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RoleConverter implements AttributeConverter<Role, String> {
    @Override
    public String convertToDatabaseColumn(Role role) {
        return role != null ? role.name() : null;
    }

    @Override
    public Role convertToEntityAttribute(String dbData) {
        return switch (dbData) {
            case "ADMIN" -> new AdminRole();
            case "MANAGER" -> new ManagerRole();
            case "USER" -> new UserRole();
            default -> throw new IllegalArgumentException("Unknown role: " + dbData);
        };
    }
}