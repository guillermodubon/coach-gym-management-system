package io.github.guillermodubon.coachgym.user.infrastructure.persistence;

import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffIdentityStateConflictException;
import io.github.guillermodubon.coachgym.user.StaffInvitationLifecyclePolicy;
import io.github.guillermodubon.coachgym.user.StaffInvitationStatus;
import io.github.guillermodubon.coachgym.user.StaffInvitationDeliveryStatus;
import io.github.guillermodubon.coachgym.user.StaffInvitationPolicy;
import io.github.guillermodubon.coachgym.user.StaffInvitationRateLimitException;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import io.github.guillermodubon.coachgym.user.application.StaffInvitationDraft;
import io.github.guillermodubon.coachgym.user.application.StaffIdentityDataAccessException;
import io.github.guillermodubon.coachgym.user.application.StaffInvitationPage;
import io.github.guillermodubon.coachgym.user.application.StaffInvitationPageQuery;
import io.github.guillermodubon.coachgym.user.application.StaffInvitationPersistence;
import io.github.guillermodubon.coachgym.user.application.StaffInvitationRecord;
import io.github.guillermodubon.coachgym.user.application.StaffInvitationSortField;
import io.github.guillermodubon.coachgym.user.application.StaffTokenFingerprint;
import io.github.guillermodubon.coachgym.user.application.StaffTokenPurpose;
import io.github.guillermodubon.coachgym.shared.security.StaffIdentityAbuseLimits;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Parameterized PostgreSQL persistence for invitation lifecycle and branch snapshots. */
@Component
class JdbcStaffInvitationPersistenceAdapter implements StaffInvitationPersistence {

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;
    private final StaffIdentityAbuseLimits abuseLimits;

    JdbcStaffInvitationPersistenceAdapter(
            JdbcClient jdbcClient,
            JdbcTemplate jdbcTemplate,
            StaffIdentityAbuseLimits abuseLimits) {
        this.jdbcClient = jdbcClient;
        this.jdbcTemplate = jdbcTemplate;
        this.abuseLimits = abuseLimits;
    }

    @Override
    @Transactional
    public StaffInvitationRecord create(StaffInvitationDraft draft) {
        if (draft == null) {
            throw new IllegalArgumentException("Invitation draft is required.");
        }
        try {
            lockInvitationEmail(draft.invitedEmail());
            if (hasExistingAccount(draft.invitedEmail())
                    || hasPendingInvitation(draft.organizationId(), draft.invitedEmail())) {
                throw new StaffIdentityStateConflictException(
                        "An account or pending invitation already exists for this email.");
            }
            lockAdministratorCreationLimit(draft.invitedByUserId());
            Integer recentInvitations = jdbcClient.sql("""
                            select count(*)
                             from gym.staff_invitations
                             where invited_by_user_id = :actorId
                               and created_at >= :cutoff
                            """)
                    .param("actorId", draft.invitedByUserId())
                    .param("cutoff", Timestamp.from(draft.createdAt()
                            .minus(abuseLimits.invitationCreationWindow())))
                    .query(Integer.class)
                    .single();
            if (recentInvitations >= abuseLimits.maxInvitationCreationsPerAdminPerHour()) {
                throw new StaffInvitationRateLimitException();
            }
            jdbcClient.sql("""
                            insert into gym.staff_invitations (
                                id, organization_id, email_normalized, proposed_role,
                                proposed_scope, token_fingerprint, token_scheme, status,
                                created_at, last_sent_at, expires_at, invited_by_user_id, version)
                            values (
                                :id, :organizationId, :email, :role, :scope,
                                :fingerprint, :scheme, 'PENDING',
                                :createdAt, :lastSentAt, :expiresAt, :invitedBy, 0)
                            """)
                    .param("id", draft.invitationId())
                    .param("organizationId", draft.organizationId())
                    .param("email", draft.invitedEmail())
                    .param("role", draft.proposedRole().name())
                    .param("scope", draft.proposedScope().name())
                    .param("fingerprint", draft.tokenFingerprint().value())
                    .param("scheme", draft.tokenFingerprint().schemeVersion())
                    .param("createdAt", StaffIdentityJdbcSupport.databaseTime(draft.createdAt()))
                    .param("lastSentAt", StaffIdentityJdbcSupport.databaseTime(draft.lastSentAt()))
                    .param("expiresAt", StaffIdentityJdbcSupport.databaseTime(draft.expiresAt()))
                    .param("invitedBy", draft.invitedByUserId())
                    .update();
            if (!draft.proposedBranchIds().isEmpty()) {
                List<UUID> proposedBranches = List.copyOf(draft.proposedBranchIds());
                jdbcTemplate.batchUpdate("""
                                insert into gym.staff_invitation_branches (invitation_id, branch_id)
                                values (?, ?)
                                """,
                        new BatchPreparedStatementSetter() {
                            @Override
                            public void setValues(PreparedStatement statement, int index)
                                    throws SQLException {
                                statement.setObject(1, draft.invitationId());
                                statement.setObject(2, proposedBranches.get(index));
                            }

                            @Override
                            public int getBatchSize() {
                                return proposedBranches.size();
                            }
                        });
            }
            return findByIdInternal(draft.invitationId())
                    .orElseThrow(() -> new StaffIdentityDataAccessException(
                            "Invitation could not be created."));
        } catch (DataAccessException exception) {
            throw StaffIdentityJdbcSupport.safeDataAccess(exception);
        }
    }

    @Override
    @Transactional
    public UUID reserveDeliveryAttempt(UUID invitationId, long invitationVersion, Instant attemptedAt) {
        if (invitationId == null || invitationVersion < 0 || attemptedAt == null) {
            throw new IllegalArgumentException("Invitation delivery reservation is incomplete.");
        }
        try {
            String recipient = jdbcClient.sql("""
                            select email_normalized
                              from gym.staff_invitations
                             where id = :invitationId
                               and version = :version
                               and status = 'PENDING'
                             for update
                            """)
                    .param("invitationId", invitationId)
                    .param("version", invitationVersion)
                    .query(String.class)
                    .optional()
                    .orElseThrow(() -> new StaffIdentityStateConflictException(
                            "Invitation is no longer available for delivery."));
            lockInvitationEmail(recipient);
            jdbcClient.sql("""
                            delete from gym.staff_invitation_delivery_attempts
                             where attempted_at < :cutoff
                            """)
                    .param("cutoff", Timestamp.from(attemptedAt
                            .minus(abuseLimits.invitationDeliveryWindow())))
                    .update();
            Integer attempts = jdbcClient.sql("""
                            select count(*)
                              from gym.staff_invitation_delivery_attempts attempt
                              join gym.staff_invitations invitation
                                on invitation.id = attempt.invitation_id
                             where invitation.email_normalized = :recipient
                               and attempt.attempted_at >= :cutoff
                            """)
                    .param("recipient", recipient)
                    .param("cutoff", Timestamp.from(attemptedAt
                            .minus(abuseLimits.invitationDeliveryWindow())))
                    .query(Integer.class)
                    .single();
            if (attempts >= abuseLimits.maxInvitationDeliveriesPerEmailPerDay()) {
                throw new StaffInvitationRateLimitException();
            }
            UUID attemptId = UUID.randomUUID();
            jdbcClient.sql("""
                            insert into gym.staff_invitation_delivery_attempts (
                                id, invitation_id, invitation_version, attempted_at, outcome)
                            values (:id, :invitationId, :version, :attemptedAt, 'RESERVED')
                            """)
                    .param("id", attemptId)
                    .param("invitationId", invitationId)
                    .param("version", invitationVersion)
                    .param("attemptedAt", StaffIdentityJdbcSupport.databaseTime(attemptedAt))
                    .update();
            return attemptId;
        } catch (DataAccessException exception) {
            throw StaffIdentityJdbcSupport.safeDataAccess(exception);
        }
    }

    @Override
    @Transactional
    public void completeDeliveryAttempt(
            UUID invitationId,
            long invitationVersion,
            StaffInvitationDeliveryStatus status,
            Instant completedAt) {
        if (invitationId == null || invitationVersion < 0 || status == null || completedAt == null) {
            throw new IllegalArgumentException("Invitation delivery outcome is incomplete.");
        }
        try {
            int updated = jdbcClient.sql("""
                            update gym.staff_invitation_delivery_attempts
                               set outcome = :outcome,
                                   completed_at = :completedAt
                             where invitation_id = :invitationId
                               and invitation_version = :version
                               and outcome = 'RESERVED'
                               and :completedAt >= attempted_at
                            """)
                    .param("outcome", status.name())
                    .param("completedAt", StaffIdentityJdbcSupport.databaseTime(completedAt))
                    .param("invitationId", invitationId)
                    .param("version", invitationVersion)
                    .update();
            if (updated != 1) {
                throw new StaffIdentityStateConflictException(
                        "Invitation delivery reservation is no longer pending.");
            }
        } catch (DataAccessException exception) {
            throw StaffIdentityJdbcSupport.safeDataAccess(exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StaffInvitationRecord> findById(UUID invitationId) {
        if (invitationId == null) {
            throw new IllegalArgumentException("Invitation identity is required.");
        }
        try {
            return findByIdInternal(invitationId);
        } catch (DataAccessException exception) {
            throw StaffIdentityJdbcSupport.safeDataAccess(exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StaffInvitationRecord> findPendingByFingerprint(
            StaffTokenFingerprint fingerprint) {
        requireFingerprint(fingerprint, StaffTokenPurpose.INVITATION);
        try {
            Optional<StaffInvitationRecord> invitation = jdbcClient.sql("""
                            select *
                              from gym.staff_invitations
                             where token_fingerprint = :fingerprint
                               and token_scheme = :scheme
                               and status = 'PENDING'
                            """)
                    .param("fingerprint", fingerprint.value())
                    .param("scheme", fingerprint.schemeVersion())
                    .query(this::mapInvitation)
                    .optional();
            return invitation.map(record -> withBranches(record,
                    branchIds(Set.of(record.invitationId()))
                            .getOrDefault(record.invitationId(), Set.of())));
        } catch (DataAccessException exception) {
            throw StaffIdentityJdbcSupport.safeDataAccess(exception);
        }
    }

    @Override
    @Transactional
    public Optional<StaffInvitationRecord> lockPendingByFingerprint(
            StaffTokenFingerprint fingerprint) {
        requireFingerprint(fingerprint, StaffTokenPurpose.INVITATION);
        try {
            Optional<StaffInvitationRecord> invitation = jdbcClient.sql("""
                            select *
                              from gym.staff_invitations
                             where token_fingerprint = :fingerprint
                               and token_scheme = :scheme
                               and status = 'PENDING'
                             for update
                            """)
                    .param("fingerprint", fingerprint.value())
                    .param("scheme", fingerprint.schemeVersion())
                    .query(this::mapInvitation)
                    .optional();
            return invitation.map(record -> withBranches(record,
                    branchIds(Set.of(record.invitationId()))
                            .getOrDefault(record.invitationId(), Set.of())));
        } catch (DataAccessException exception) {
            throw StaffIdentityJdbcSupport.safeDataAccess(exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public StaffInvitationPage findPage(StaffInvitationPageQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("Invitation page query is required.");
        }
        try {
            StringBuilder sql = new StringBuilder("""
                    select invitation.id
                      from gym.staff_invitations invitation
                     where invitation.organization_id = :organizationId
                    """);
            Map<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("organizationId", query.organizationId());
            if (query.status() != null) {
                sql.append(" and invitation.status = :status");
                parameters.put("status", query.status().name());
            }
            if (query.role() != null) {
                sql.append(" and invitation.proposed_role = :role");
                parameters.put("role", query.role().name());
            }
            if (query.scope() != null) {
                sql.append(" and invitation.proposed_scope = :scope");
                parameters.put("scope", query.scope().name());
            }
            if (query.branchId() != null) {
                sql.append(" and exists (select 1 from gym.staff_invitation_branches branch_filter")
                        .append(" where branch_filter.invitation_id = invitation.id")
                        .append(" and branch_filter.branch_id = :branchId)");
                parameters.put("branchId", query.branchId());
            }
            if (query.emailQuery() != null) {
                sql.append(" and invitation.email_normalized like :emailQuery");
                parameters.put("emailQuery", "%" + query.emailQuery() + "%");
            }
            appendInstantFilter(sql, parameters, "created_at", "createdFrom", query.createdFrom(), true);
            appendInstantFilter(sql, parameters, "created_at", "createdTo", query.createdTo(), false);
            appendInstantFilter(sql, parameters, "expires_at", "expiresFrom", query.expiresFrom(), true);
            appendInstantFilter(sql, parameters, "expires_at", "expiresTo", query.expiresTo(), false);
            sql.append(" order by ")
                    .append(sortColumn(query.sort()))
                    .append(' ')
                    .append(query.direction().name())
                    .append(", invitation.id asc limit :limit offset :offset");
            parameters.put("limit", query.pageSize() + 1);
            parameters.put("offset", (long) query.page() * query.pageSize());
            var statement = jdbcClient.sql(sql.toString());
            for (Map.Entry<String, Object> parameter : parameters.entrySet()) {
                statement = statement.param(parameter.getKey(), parameter.getValue());
            }
            List<UUID> ids = statement.query((resultSet, rowNum) ->
                    StaffIdentityJdbcSupport.uuid(resultSet, "id")).list();
            boolean hasNext = ids.size() > query.pageSize();
            List<UUID> pageIds = hasNext
                    ? List.copyOf(ids.subList(0, query.pageSize()))
                    : List.copyOf(ids);
            if (pageIds.isEmpty()) {
                return new StaffInvitationPage(List.of(), false);
            }
            Map<UUID, StaffInvitationRecord> rows = new LinkedHashMap<>();
            jdbcClient.sql("""
                            select *
                              from gym.staff_invitations
                             where id in (:ids)
                            """)
                    .param("ids", pageIds)
                    .query(this::mapInvitation)
                    .list()
                    .forEach(record -> rows.put(record.invitationId(), record));
            Map<UUID, Set<UUID>> branches = branchIds(new LinkedHashSet<>(pageIds));
            List<StaffInvitationRecord> records = new ArrayList<>(pageIds.size());
            for (UUID id : pageIds) {
                StaffInvitationRecord record = rows.get(id);
                if (record != null) {
                    records.add(withBranches(record, branches.getOrDefault(id, Set.of())));
                }
            }
            return new StaffInvitationPage(records, hasNext);
        } catch (DataAccessException exception) {
            throw StaffIdentityJdbcSupport.safeDataAccess(exception);
        }
    }

    private static void appendInstantFilter(
            StringBuilder sql,
            Map<String, Object> parameters,
            String column,
            String parameter,
            Instant value,
            boolean lowerBound) {
        if (value == null) {
            return;
        }
        sql.append(" and invitation.").append(column)
                .append(lowerBound ? " >= :" : " <= :").append(parameter);
        parameters.put(parameter, StaffIdentityJdbcSupport.databaseTime(value));
    }

    private static String sortColumn(StaffInvitationSortField sort) {
        return switch (sort) {
            case CREATED_AT -> "invitation.created_at";
            case EXPIRES_AT -> "invitation.expires_at";
            case STATUS -> "invitation.status";
            case PROPOSED_ROLE -> "invitation.proposed_role";
        };
    }

    @Override
    @Transactional
    public StaffInvitationRecord transition(
            UUID invitationId,
            StaffInvitationStatus target,
            Instant occurredAt,
            UUID acceptedUserId,
            long expectedVersion) {
        requireVersion(expectedVersion);
        if (invitationId == null || target == null || occurredAt == null) {
            throw new IllegalArgumentException("Invitation transition is incomplete.");
        }
        StaffInvitationLifecyclePolicy.requireTransition(StaffInvitationStatus.PENDING, target);
        if ((target == StaffInvitationStatus.ACCEPTED) != (acceptedUserId != null)) {
            throw new IllegalArgumentException("Accepted account details do not match the transition.");
        }
        try {
            int updated = jdbcClient.sql("""
                            update gym.staff_invitations
                               set status = :target,
                                   accepted_at = case when :target = 'ACCEPTED' then :occurredAt else null end,
                                   accepted_user_id = case when :target = 'ACCEPTED'
                                       then cast(:acceptedUserId as uuid) else null end,
                                   expired_at = case when :target = 'EXPIRED' then :occurredAt else null end,
                                   revoked_at = case when :target = 'REVOKED' then :occurredAt else null end,
                                   version = version + 1
                             where id = :id
                               and status = 'PENDING'
                               and version = :expectedVersion
                               and ((:target = 'ACCEPTED' and expires_at > :occurredAt)
                                 or (:target = 'EXPIRED' and expires_at <= :occurredAt)
                                 or :target = 'REVOKED')
                            """)
                    .param("target", target.name())
                    .param("occurredAt", StaffIdentityJdbcSupport.databaseTime(occurredAt))
                    .param("acceptedUserId", acceptedUserId)
                    .param("id", invitationId)
                    .param("expectedVersion", expectedVersion)
                    .update();
            if (updated != 1) {
                throw new StaffIdentityStateConflictException(
                        "Invitation is no longer pending or its version changed.");
            }
            return findByIdInternal(invitationId).orElseThrow(() ->
                    new StaffIdentityStateConflictException("Invitation is no longer available."));
        } catch (DataAccessException exception) {
            throw StaffIdentityJdbcSupport.safeDataAccess(exception);
        }
    }

    @Override
    @Transactional
    public StaffInvitationRecord rotatePendingToken(
            UUID invitationId,
            StaffTokenFingerprint replacementFingerprint,
            Instant sentAt,
            Instant expiresAt,
            long expectedVersion) {
        requireVersion(expectedVersion);
        requireFingerprint(replacementFingerprint, StaffTokenPurpose.INVITATION);
        if (invitationId == null || sentAt == null || expiresAt == null) {
            throw new IllegalArgumentException("Invitation token rotation is incomplete.");
        }
        try {
            int updated = jdbcClient.sql("""
                            update gym.staff_invitations
                               set token_fingerprint = :fingerprint,
                                   token_scheme = :scheme,
                                   last_sent_at = :sentAt,
                                   expires_at = :expiresAt,
                                   version = version + 1
                             where id = :id
                               and status = 'PENDING'
                               and version = :expectedVersion
                               and token_fingerprint <> :fingerprint
                               and last_sent_at < :sentAt
                               and expires_at > :sentAt
                            """)
                    .param("fingerprint", replacementFingerprint.value())
                    .param("scheme", replacementFingerprint.schemeVersion())
                    .param("sentAt", StaffIdentityJdbcSupport.databaseTime(sentAt))
                    .param("expiresAt", StaffIdentityJdbcSupport.databaseTime(expiresAt))
                    .param("id", invitationId)
                    .param("expectedVersion", expectedVersion)
                    .update();
            if (updated != 1) {
                throw new StaffIdentityStateConflictException(
                        "Pending invitation could not be rotated at the supplied version.");
            }
            return findByIdInternal(invitationId).orElseThrow(() ->
                    new StaffIdentityStateConflictException("Invitation is no longer available."));
        } catch (DataAccessException exception) {
            throw StaffIdentityJdbcSupport.safeDataAccess(exception);
        }
    }

    private Optional<StaffInvitationRecord> findByIdInternal(UUID invitationId) {
        Optional<StaffInvitationRecord> invitation = jdbcClient.sql("""
                        select * from gym.staff_invitations where id = :id
                        """)
                .param("id", invitationId)
                .query(this::mapInvitation)
                .optional();
        return invitation.map(record -> withBranches(record,
                branchIds(Set.of(record.invitationId()))
                        .getOrDefault(record.invitationId(), Set.of())));
    }

    private boolean hasExistingAccount(String normalizedEmail) {
        return Boolean.TRUE.equals(jdbcClient.sql("""
                        select exists (
                            select 1 from gym.users
                             where lower(btrim(email)) = :email
                        )
                        """)
                .param("email", normalizedEmail)
                .query(Boolean.class)
                .single());
    }

    private boolean hasPendingInvitation(UUID organizationId, String normalizedEmail) {
        return Boolean.TRUE.equals(jdbcClient.sql("""
                        select exists (
                            select 1 from gym.staff_invitations
                             where organization_id = :organizationId
                               and email_normalized = :email
                               and status = 'PENDING'
                        )
                        """)
                .param("organizationId", organizationId)
                .param("email", normalizedEmail)
                .query(Boolean.class)
                .single());
    }

    private void lockInvitationEmail(String normalizedEmail) {
        jdbcClient.sql("""
                        select pg_advisory_xact_lock(
                            hashtextextended('staff-invitation-email:' || :email, 0))
                        """)
                .param("email", normalizedEmail)
                .query((resultSet, rowNum) -> Boolean.TRUE)
                .single();
    }

    private void lockAdministratorCreationLimit(UUID actorId) {
        jdbcClient.sql("""
                        select pg_advisory_xact_lock(
                            hashtextextended('staff-invitation-admin:' || cast(:actorId as text), 0))
                        """)
                .param("actorId", actorId)
                .query((resultSet, rowNum) -> Boolean.TRUE)
                .single();
    }

    private Map<UUID, Set<UUID>> branchIds(Set<UUID> invitationIds) {
        if (invitationIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Set<UUID>> result = new LinkedHashMap<>();
        jdbcClient.sql("""
                        select invitation_id, branch_id
                          from gym.staff_invitation_branches
                         where invitation_id in (:ids)
                         order by invitation_id, branch_id
                        """)
                .param("ids", invitationIds)
                .query((resultSet, rowNum) -> Map.entry(
                        StaffIdentityJdbcSupport.uuid(resultSet, "invitation_id"),
                        StaffIdentityJdbcSupport.uuid(resultSet, "branch_id")))
                .list()
                .forEach(entry -> result.computeIfAbsent(entry.getKey(), ignored -> new LinkedHashSet<>())
                        .add(entry.getValue()));
        return result;
    }

    private StaffInvitationRecord mapInvitation(ResultSet resultSet, int rowNum) throws SQLException {
        return new StaffInvitationRecord(
                StaffIdentityJdbcSupport.uuid(resultSet, "id"),
                StaffIdentityJdbcSupport.uuid(resultSet, "organization_id"),
                resultSet.getString("email_normalized"),
                RoleCode.valueOf(resultSet.getString("proposed_role")),
                StaffScopeType.valueOf(resultSet.getString("proposed_scope")),
                Set.of(),
                StaffInvitationStatus.valueOf(resultSet.getString("status")),
                new StaffTokenFingerprint(
                        resultSet.getString("token_fingerprint").stripTrailing(),
                        resultSet.getString("token_scheme")),
                StaffIdentityJdbcSupport.instant(resultSet, "created_at"),
                StaffIdentityJdbcSupport.instant(resultSet, "last_sent_at"),
                StaffIdentityJdbcSupport.instant(resultSet, "expires_at"),
                StaffIdentityJdbcSupport.nullableInstant(resultSet, "accepted_at"),
                StaffIdentityJdbcSupport.uuid(resultSet, "accepted_user_id"),
                StaffIdentityJdbcSupport.nullableInstant(resultSet, "expired_at"),
                StaffIdentityJdbcSupport.nullableInstant(resultSet, "revoked_at"),
                StaffIdentityJdbcSupport.uuid(resultSet, "invited_by_user_id"),
                resultSet.getLong("version"));
    }

    private static StaffInvitationRecord withBranches(
            StaffInvitationRecord record,
            Set<UUID> branches) {
        return new StaffInvitationRecord(
                record.invitationId(), record.organizationId(), record.invitedEmail(),
                record.proposedRole(), record.proposedScope(), branches, record.status(),
                record.tokenFingerprint(), record.createdAt(), record.lastSentAt(), record.expiresAt(),
                record.acceptedAt(), record.acceptedUserId(), record.expiredAt(), record.revokedAt(),
                record.invitedByUserId(), record.version());
    }

    private static void requireFingerprint(
            StaffTokenFingerprint fingerprint,
            StaffTokenPurpose purpose) {
        if (fingerprint == null || !purpose.schemeVersion().equals(fingerprint.schemeVersion())) {
            throw new IllegalArgumentException("Staff invitation fingerprint is invalid.");
        }
    }

    private static void requireVersion(long expectedVersion) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("Expected version is invalid.");
        }
    }
}
