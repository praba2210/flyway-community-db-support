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

import org.flywaydb.core.extensibility.ConfigurationExtension;

/**
 * Configuration for Aurora DSQL OCC retry behavior, under the {@code dsql} namespace.
 *
 * <p>On by default. Override via {@code flyway.dsql.occMaxRetries} /
 * {@code flyway.dsql.occMaxRetryDelaySeconds} (or the matching {@code FLYWAY_DSQL_*}
 * environment variables). Defaults mirror the Aurora DSQL EF Core adapter (6 retries, 30s cap).
 *
 * <p>Must remain a pure JavaBean: Flyway deep-copies it via {@link #copy()} using Jackson.
 * Do not store computed / non-bean state here.
 */
public class DSQLConfigurationExtension implements ConfigurationExtension {

    private static final String ENV_MAX_RETRIES = "FLYWAY_DSQL_OCC_MAX_RETRIES";
    private static final String ENV_MAX_RETRY_DELAY_SECONDS = "FLYWAY_DSQL_OCC_MAX_RETRY_DELAY_SECONDS";

    private int occMaxRetries = 6;
    private int occMaxRetryDelaySeconds = 30;

    public int getOccMaxRetries() {
        return occMaxRetries;
    }

    public void setOccMaxRetries(int occMaxRetries) {
        this.occMaxRetries = occMaxRetries;
    }

    public int getOccMaxRetryDelaySeconds() {
        return occMaxRetryDelaySeconds;
    }

    public void setOccMaxRetryDelaySeconds(int occMaxRetryDelaySeconds) {
        this.occMaxRetryDelaySeconds = occMaxRetryDelaySeconds;
    }

    @Override
    public String getNamespace() {
        return "dsql";
    }

    @Override
    public String getConfigurationParameterFromEnvironmentVariable(String environmentVariable) {
        switch (environmentVariable) {
            case ENV_MAX_RETRIES:
                return "flyway.dsql.occMaxRetries";
            case ENV_MAX_RETRY_DELAY_SECONDS:
                return "flyway.dsql.occMaxRetryDelaySeconds";
            default:
                return null;
        }
    }
}
