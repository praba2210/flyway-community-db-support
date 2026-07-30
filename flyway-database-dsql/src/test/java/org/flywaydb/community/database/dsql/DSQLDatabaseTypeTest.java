/*-
 * ========================LICENSE_START=================================
 * flyway-database-dsql
 * ========================================================================
 * Copyright (C) 2010 - 2026 Red Gate Software Ltd
 * ========================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.flywaydb.community.database.dsql;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.internal.database.base.CommunityDatabaseType;
import org.flywaydb.core.internal.jdbc.ExecutionTemplate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DSQLDatabaseTypeTest {

    private final DSQLDatabaseType databaseType = new DSQLDatabaseType();

    @Test
    void nameIsAuroraDsql() {
        assertThat(databaseType.getName()).isEqualTo("Aurora DSQL");
    }

    @Test
    void isCommunityDatabaseType() {
        assertThat(databaseType).isInstanceOf(CommunityDatabaseType.class);
    }

    @Test
    void handlesAwsDsqlUrls() {
        assertThat(databaseType.handlesJDBCUrl("jdbc:aws-dsql:postgresql://abc123.dsql.us-east-1.on.aws/postgres")).isTrue();
        assertThat(databaseType.handlesJDBCUrl("jdbc:aws-dsql:postgresql://abc123.dsql.us-east-1.on.aws:5432/postgres")).isTrue();
        assertThat(databaseType.handlesJDBCUrl("jdbc:aws-dsql://abc123.dsql.us-east-1.on.aws/postgres")).isTrue();
    }

    @Test
    void handlesTransformedPostgresqlUrlsWithDsqlEndpoint() {
        assertThat(databaseType.handlesJDBCUrl("jdbc:postgresql://abc123.dsql.us-east-1.on.aws:5432/postgres")).isTrue();
        assertThat(databaseType.handlesJDBCUrl("jdbc:postgresql://xyz789.dsql.eu-west-1.on.aws:5432/postgres")).isTrue();
    }

    @Test
    void handlesPrivateLinkEndpoints() {
        assertThat(databaseType.handlesJDBCUrl("jdbc:postgresql://abc123.dsql-fnh4.us-east-1.on.aws:5432/postgres")).isTrue();
        assertThat(databaseType.handlesJDBCUrl("jdbc:aws-dsql:postgresql://abc123.dsql-fnh4.us-east-1.on.aws:5432/postgres")).isTrue();
    }

    @Test
    void doesNotHandleNonDsqlUrls() {
        assertThat(databaseType.handlesJDBCUrl("jdbc:postgresql://localhost:5432/mydb")).isFalse();
        assertThat(databaseType.handlesJDBCUrl("jdbc:postgresql://mydb.abc123.us-east-1.rds.amazonaws.com:5432/mydb")).isFalse();
        assertThat(databaseType.handlesJDBCUrl("jdbc:mysql://localhost:3306/mydb")).isFalse();
        assertThat(databaseType.handlesJDBCUrl("jdbc:oracle:thin:@localhost:1521:xe")).isFalse();
    }

    @Test
    void doesNotHandleDsqlPatternOutsideHost() {
        // The DSQL pattern must match the host only: a ".dsql." in the database name or a query
        // parameter must not hijack an ordinary PostgreSQL URL (priority 1 would win selection).
        assertThat(databaseType.handlesJDBCUrl("jdbc:postgresql://localhost:5432/app.dsql.production")).isFalse();
        assertThat(databaseType.handlesJDBCUrl("jdbc:postgresql://localhost:5432/app?ApplicationName=.dsql.")).isFalse();
    }

    @Test
    void priorityIsHigherThanPostgres() {
        assertThat(databaseType.getPriority()).isGreaterThan(0);
    }

    @Test
    void driverClassIsDsqlConnectorForAwsDsqlUrls() {
        assertThat(databaseType.getDriverClass("jdbc:aws-dsql:postgresql://abc123.dsql.us-east-1.on.aws/postgres", null))
                .isEqualTo("software.amazon.dsql.jdbc.DSQLConnector");
    }

    @Test
    void driverClassIsPostgresForTransformedUrls() {
        assertThat(databaseType.getDriverClass("jdbc:postgresql://abc123.dsql.us-east-1.on.aws:5432/postgres", null))
                .isEqualTo("org.postgresql.Driver");
    }

    @Test
    void wrapsTransactionalTemplateWithOccRetryDecorator() {
        // The commit-wrapping seam must return our OCC-retry decorator, otherwise a commit-time
        // DSQL conflict is never retried. Base PostgreSQL just news up a TransactionalExecutionTemplate
        // holding the connection (no I/O), so a null connection is fine for the type check.
        ExecutionTemplate template = databaseType.createTransactionalExecutionTemplate(null, true);
        assertThat(template).isInstanceOf(DSQLExecutionTemplate.class);
    }

    @Test
    void wrapsSqlScriptExecutorFactoryForAsyncIndexWait() {
        // The SQL-script executor seam must return our wrapper so CREATE INDEX ASYNC waits.
        // Base PostgreSQL builds the delegate factory lazily (no I/O until an executor is made),
        // so null collaborators are fine for the type check.
        org.flywaydb.core.internal.sqlscript.SqlScriptExecutorFactory factory =
                databaseType.createSqlScriptExecutorFactory(null, null, null);
        assertThat(factory).isInstanceOf(DSQLSqlScriptExecutorFactory.class);
    }

    @Test
    void maxRetryDelayConvertsSecondsToMillis() {
        // Guards the seconds->millis conversion: dropping the *1000 would make backoff 1000x too short.
        assertThat(DSQLDatabaseType.maxRetryDelayMillis(30)).isEqualTo(30_000L);
    }

    @Test
    void maxRetryDelayFloorsAtMinimum() {
        // A zero or negative configured cap must not disable backoff; it floors at 100ms.
        assertThat(DSQLDatabaseType.maxRetryDelayMillis(0)).isEqualTo(100L);
        assertThat(DSQLDatabaseType.maxRetryDelayMillis(-5)).isEqualTo(100L);
    }

    @Test
    void resolveKnobsFallsBackToDefaultsWhenConfigNull() {
        assertThat(DSQLDatabaseType.resolveOccRetryKnobs(null)).containsExactly(6, 30);
    }

    @Test
    void resolveKnobsReadsConfiguredExtensionValues() {
        // Drives the real resolution path: FluentConfiguration auto-registers the extension via
        // ServiceLoader, so this proves configured values actually reach createTransactionalExecutionTemplate.
        FluentConfiguration config = new FluentConfiguration();
        DSQLConfigurationExtension ext =
                config.getPluginRegister().getPlugin(DSQLConfigurationExtension.class);
        ext.setOccMaxRetries(3);
        ext.setOccMaxRetryDelaySeconds(10);
        assertThat(DSQLDatabaseType.resolveOccRetryKnobs(config)).containsExactly(3, 10);
    }

    @Test
    void resolveKnobsClampsNegativeRetriesToZero() {
        FluentConfiguration config = new FluentConfiguration();
        config.getPluginRegister().getPlugin(DSQLConfigurationExtension.class).setOccMaxRetries(-1);
        assertThat(DSQLDatabaseType.resolveOccRetryKnobs(config)[0]).isZero();
    }

    @Test
    void resolveKnobsClampsExcessiveRetriesToConnectorMax() {
        // OCCRetryConfig.build() rejects maxRetries > 100; the clamp keeps an oversized config value
        // from failing the migration when building the retry config.
        FluentConfiguration config = new FluentConfiguration();
        config.getPluginRegister().getPlugin(DSQLConfigurationExtension.class).setOccMaxRetries(500);
        assertThat(DSQLDatabaseType.resolveOccRetryKnobs(config)[0]).isEqualTo(100);
    }
}
