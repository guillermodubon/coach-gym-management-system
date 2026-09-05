package io.github.guillermodubon.coachgym.user.infrastructure.persistence;

import io.github.guillermodubon.coachgym.user.ActiveStaffDirectory;
import io.github.guillermodubon.coachgym.user.ActiveStaffMember;
import io.github.guillermodubon.coachgym.user.RoleCode;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class ActiveStaffDirectoryAdapter implements ActiveStaffDirectory {

    private final JdbcTemplate jdbcTemplate;

    ActiveStaffDirectoryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ActiveStaffMember> findActiveById(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        List<StaffRoleRow> rows = jdbcTemplate.query("""
                select u.id, u.username, r.role_code
                from gym.users u
                left join gym.user_roles ur on ur.user_id = u.id
                left join gym.roles r on r.id = ur.role_id
                where u.id = ? and u.status = 'ACTIVE'
                order by r.role_code
                """,
                (resultSet, rowNumber) -> new StaffRoleRow(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("username"),
                        resultSet.getString("role_code")),
                userId);
        return aggregate(rows).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActiveStaffMember> findActiveByRole(RoleCode roleCode) {
        if (roleCode == null) {
            throw new IllegalArgumentException("Staff role is required.");
        }
        List<StaffRoleRow> rows = jdbcTemplate.query("""
                select distinct u.id, u.username, all_roles.role_code
                from gym.users u
                join gym.user_roles requested_ur on requested_ur.user_id = u.id
                join gym.roles requested_role on requested_role.id = requested_ur.role_id
                left join gym.user_roles all_ur on all_ur.user_id = u.id
                left join gym.roles all_roles on all_roles.id = all_ur.role_id
                where u.status = 'ACTIVE' and requested_role.role_code = ?
                order by u.username, all_roles.role_code
                """,
                (resultSet, rowNumber) -> new StaffRoleRow(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("username"),
                        resultSet.getString("role_code")),
                roleCode.name());
        return aggregate(rows);
    }

    private static List<ActiveStaffMember> aggregate(List<StaffRoleRow> rows) {
        Map<UUID, MutableStaff> staff = new LinkedHashMap<>();
        for (StaffRoleRow row : rows) {
            MutableStaff member = staff.computeIfAbsent(
                    row.userId(), id -> new MutableStaff(id, row.username()));
            if (row.roleCode() != null) {
                member.roles.add(RoleCode.valueOf(row.roleCode()));
            }
        }
        return staff.values().stream()
                .map(member -> new ActiveStaffMember(
                        member.userId, member.username, Set.copyOf(member.roles)))
                .toList();
    }

    private record StaffRoleRow(UUID userId, String username, String roleCode) {
    }

    private static final class MutableStaff {
        private final UUID userId;
        private final String username;
        private final EnumSet<RoleCode> roles = EnumSet.noneOf(RoleCode.class);

        private MutableStaff(UUID userId, String username) {
            this.userId = userId;
            this.username = username;
        }
    }
}
