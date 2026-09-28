package in.unshare.repository;

import in.unshare.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the Flyway migration actually produced the indexes and foreign keys the
 * persistence design relies on, independent of any JPA-level behavior.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class SchemaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayMigrationRanSuccessfully() {
        List<String> versions = jdbcTemplate.queryForList(
                "select version from flyway_schema_history where success = true order by installed_rank", String.class);

        assertThat(versions).contains("1");
    }

    @Test
    void expectedIndexesExistOnShareSession() {
        assertThat(indexNames("share_session")).contains(
                "uq_share_session_token",
                "idx_share_session_status",
                "idx_share_session_expires_at",
                "idx_share_session_last_activity_at");
    }

    @Test
    void expectedIndexesExistOnTransferSession() {
        assertThat(indexNames("transfer_session")).contains(
                "idx_transfer_session_share_session_id",
                "idx_transfer_session_status",
                "idx_transfer_session_last_activity_at");
    }

    @Test
    void expectedIndexesExistOnSharedFile() {
        assertThat(indexNames("shared_file")).contains(
                "idx_shared_file_transfer_session_id",
                "uq_shared_file_transfer_sequence");
    }

    @Test
    void foreignKeysCascadeOnDelete() {
        List<String> deleteRules = jdbcTemplate.queryForList("""
                select rc.delete_rule
                from information_schema.referential_constraints rc
                join information_schema.table_constraints tc
                  on tc.constraint_name = rc.constraint_name
                where tc.table_name in ('transfer_session', 'shared_file')
                """, String.class);

        assertThat(deleteRules).allMatch("CASCADE"::equals);
    }

    private List<String> indexNames(String tableName) {
        return jdbcTemplate.queryForList(
                "select indexname from pg_indexes where tablename = ?", String.class, tableName);
    }
}
