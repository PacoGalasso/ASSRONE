package ASSRONE.backend.config;

import org.flywaydb.core.api.callback.BaseCallback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Statement;

/**
 * Establishes the five tables that predate Flyway's introduction to this
 * project — users, membership_applications, events, event_registrations,
 * documents — so that V1 (needs membership_applications), V2/V6
 * (events/users) and V3 (documents) always have something to operate on,
 * on every single database this application will ever run against,
 * including one that has never run this application before.
 *
 * <p><b>Why this exists</b>: before Flyway was added to this project, these
 * five tables were created and evolved by Hibernate's ddl-auto alone. By
 * the time Flyway's first migration (V1) was written, it could safely
 * assume membership_applications already existed everywhere this
 * application was actually running — because it always was, in every real
 * environment. That assumption broke down the moment this application
 * needed to boot correctly with Hibernate never touching the schema at
 * all (ddl-auto=validate, unconditionally, in every environment — see
 * application.properties): something has to create these five tables
 * before V1 runs, and it can no longer be Hibernate. event_registrations
 * was originally missing from this list — no Flyway migration has ever
 * referenced it, so a genuinely fresh database never got it at all, and
 * Hibernate's schema validation failed on it specifically (see
 * EventRegistrationsBaselineIntegrationTest for the regression coverage).
 *
 * <p><b>Why a callback, not a new migration</b>: V1 through V8 are already
 * applied, checksummed, and recorded in flyway_schema_history on every
 * real database this application runs on (production, and any developer's
 * already-bootstrapped local database). Editing any of their contents
 * would change their checksum and fail validation everywhere they've
 * already run; inserting a new migration numbered before V1 is equally
 * unsafe — Flyway's default (and this project's) configuration does not
 * apply out-of-order migrations, so a new V0.x introduced after V1..V8
 * are already recorded would either be silently ignored or rejected,
 * neither of which is the "runs before V1" guarantee actually needed. A
 * BEFORE_MIGRATE callback runs immediately before Flyway applies any
 * versioned migration, on every single flyway.migrate() call, completely
 * outside flyway_schema_history — exactly the right place for a one-time,
 * safe-to-repeat prerequisite, with none of the numbering hazards above.
 *
 * <p><b>Why this is always safe to run</b>: every statement below is
 * {@code CREATE TABLE IF NOT EXISTS}. On any database that already has
 * these tables — production, an already-bootstrapped local database, or
 * this same database on its second and every subsequent boot — this is a
 * complete no-op, verified by Postgres itself purely from each table's
 * name, regardless of whatever columns that already-existing table
 * actually has. Nothing here ever runs an ALTER, a DROP, or touches a
 * single existing row.
 *
 * <p>Each table's shape reflects exactly what it looked like the moment
 * before Flyway's own migrations started evolving it further, so every
 * later migration in db/migration still has exactly the work it was
 * written to do, unmodified:
 * <ul>
 *   <li>documents has no visibility column — V3 adds it (nullable first,
 *   backfilled, then constrained);</li>
 *   <li>users has no unique constraint on username — V6 adds it;</li>
 *   <li>events.type has no CHECK constraint — V2's own header explains
 *   this exact scenario: "A freshly created database never had the old
 *   [enum-based] mapping applied, so it never had the constraint in the
 *   first place" — V2's DROP CONSTRAINT IF EXISTS is then a correct no-op.</li>
 * </ul>
 * event_registrations is the exception: no migration ever evolves it
 * further, so its full, current shape (event_id FK to events, the
 * event_id/normalized_email unique constraint EventRegistration#uniqueConstraints
 * declares, and every column exactly as EventRegistration maps it) is
 * created here in one shot, matching the entity exactly.
 *
 * <p>The membership_type/status/visibility-adjacent CHECK constraints
 * Hibernate used to generate implicitly for @Enumerated(STRING) columns
 * (see V2's and V3's own comments) are recreated here explicitly for
 * membership_applications, rather than left to chance: unlike the
 * events.type case, these enums (MembershipType, ApplicationStatus) are
 * still enums in the current mapping and their value sets are stable, so
 * there is no reason to lose that guarantee for a freshly bootstrapped
 * database. documents.visibility gets its own equivalent CHECK when V3
 * adds the column, matching the same rationale — not duplicated here
 * since the column doesn't exist yet at this point.
 */
@Component
public class LegacyBaselineFlywayCallback extends BaseCallback {

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.BEFORE_MIGRATE;
    }

    @Override
    public void handle(Event event, Context context) {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        email VARCHAR(255) NOT NULL UNIQUE,
                        password VARCHAR(255) NOT NULL,
                        username VARCHAR(255) NOT NULL,
                        first_name VARCHAR(255),
                        last_name VARCHAR(255),
                        avatar_filename VARCHAR(255),
                        role VARCHAR(255) NOT NULL,
                        is_active BOOLEAN NOT NULL,
                        last_login TIMESTAMP,
                        failed_login_attempts INTEGER NOT NULL DEFAULT 0,
                        locked_until TIMESTAMP,
                        created_at TIMESTAMP NOT NULL,
                        updated_at TIMESTAMP NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS membership_applications (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        full_name VARCHAR(255) NOT NULL,
                        email VARCHAR(255) NOT NULL,
                        phone VARCHAR(255),
                        membership_type VARCHAR(255) NOT NULL
                            CHECK (membership_type IN ('INDIVIDUEL', 'COLLECTIF')),
                        message VARCHAR(1000),
                        charter_accepted BOOLEAN NOT NULL,
                        status VARCHAR(255) NOT NULL
                            CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
                        submitted_at TIMESTAMP NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS events (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        title VARCHAR(255) NOT NULL,
                        description VARCHAR(1000),
                        type VARCHAR(255) NOT NULL,
                        event_date DATE NOT NULL,
                        start_time TIME,
                        end_time TIME,
                        location VARCHAR(255),
                        max_participants INTEGER,
                        current_participants INTEGER
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS event_registrations (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        event_id BIGINT NOT NULL REFERENCES events (id),
                        full_name VARCHAR(150) NOT NULL,
                        email VARCHAR(254) NOT NULL,
                        normalized_email VARCHAR(254) NOT NULL,
                        created_at TIMESTAMP NOT NULL,
                        CONSTRAINT uk_event_registration_event_email UNIQUE (event_id, normalized_email)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS documents (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        title VARCHAR(255) NOT NULL,
                        description VARCHAR(500),
                        original_filename VARCHAR(255) NOT NULL,
                        stored_filename VARCHAR(255) NOT NULL UNIQUE,
                        content_type VARCHAR(255) NOT NULL,
                        file_size BIGINT NOT NULL,
                        uploaded_by VARCHAR(255) NOT NULL,
                        uploaded_at TIMESTAMP NOT NULL
                    )
                    """);
        } catch (SQLException ex) {
            throw new IllegalStateException(
                    "Impossible d'établir le schéma de base (tables antérieures à Flyway) avant l'exécution des migrations.",
                    ex);
        }
    }

    @Override
    public String getCallbackName() {
        return "legacyBaseline";
    }
}
