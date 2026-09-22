package ru.repairradar.address;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProgramSchemaIT extends PostgresIntegrationSupport {

    @Test
    void schemaHasHouseFlagProgramTablesAndJobTable() {
        assertThat(jdbc.queryForObject("""
                select column_default from information_schema.columns
                where table_name = 'repair_houses' and column_name = 'program_data_loaded'
                """, String.class)).isEqualTo("false");
        for (String table : List.of("program_houses", "program_works", "program_jobs")) {
            assertThat(jdbc.queryForObject("select to_regclass('public." + table + "')", String.class))
                    .isNotNull();
        }
    }
}