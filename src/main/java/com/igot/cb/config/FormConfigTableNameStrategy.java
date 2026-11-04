package com.igot.cb.config;

import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.QueryRewriter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes the "form_configuration" table name configurable via form.config.db.tableName:
 * - as the Hibernate physical naming strategy, for FormConfigurationEntity's mapping. Extends Spring
 *   Boot's default so all other names stay snake_case (e.g. createdAt -> created_at).
 * - as a QueryRewriter, for the native @Query lookups in FormConfigurationRepository.
 * Spring Boot and Spring Data pick this bean up automatically.
 */
@Component
public class FormConfigTableNameStrategy
        extends CamelCaseToUnderscoresNamingStrategy implements QueryRewriter {

    private static final String DEFAULT_TABLE_NAME = "form_configuration";

    // The table name is spliced into native SQL, so only plain identifiers are allowed.
    private static final Pattern VALID_TABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private static final Pattern TABLE_IN_QUERY = Pattern.compile("\\b" + DEFAULT_TABLE_NAME + "\\b");

    private final String formConfigurationTable;

    // True when the configured name is the default one, so native queries need no rewriting.
    private final boolean usesDefaultTable;

    // Rewritten SQL per original query text; the repository only has a fixed handful of native queries.
    private final Map<String, String> rewrittenQueries = new ConcurrentHashMap<>();

    public FormConfigTableNameStrategy(
            @Value("${form.config.db.tableName:" + DEFAULT_TABLE_NAME + "}") String tableName) {
        if (tableName == null || !VALID_TABLE_NAME.matcher(tableName.trim()).matches()) {
            throw new IllegalArgumentException("Invalid form.config.db.tableName: '" + tableName + "'");
        }
        this.formConfigurationTable = tableName.trim();
        this.usesDefaultTable = DEFAULT_TABLE_NAME.equals(this.formConfigurationTable);
    }

    @Override
    public Identifier toPhysicalTableName(
            Identifier name,
            JdbcEnvironment jdbcEnvironment) {

        if (name != null && DEFAULT_TABLE_NAME.equalsIgnoreCase(name.getText())) {
            return Identifier.toIdentifier(formConfigurationTable);
        }

        return super.toPhysicalTableName(name, jdbcEnvironment);
    }

    @Override
    @NonNull
    public String rewrite(@NonNull String query, @NonNull Sort sort) {
        if (usesDefaultTable) {
            return query;
        }
        return rewrittenQueries.computeIfAbsent(query,
                q -> TABLE_IN_QUERY.matcher(q).replaceAll(Matcher.quoteReplacement(formConfigurationTable)));
    }
}
