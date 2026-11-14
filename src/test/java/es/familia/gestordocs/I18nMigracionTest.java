package es.familia.gestordocs;

import static org.assertj.core.api.Assertions.*;

import java.sql.SQLException;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

@TestIntegracion
class I18nMigracionTest {
    @Autowired DataSource dataSource;

    @Test
    void migracionConDatosPreviosAplicaDefectoYRestriccion() throws Exception {
        // Esquema privado transaccional: hasta su creación se deshace al acabar.
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var sql = connection.createStatement()) {
                sql.execute("CREATE SCHEMA prueba_migracion_idioma");
                sql.execute("SET LOCAL search_path TO prueba_migracion_idioma");
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V1__usuarios_personas.sql"));
                sql.execute("INSERT INTO usuario(username,password_hash,rol) VALUES ('previo','hash','USER')");
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V5__idioma_usuario.sql"));
                sql.execute("INSERT INTO usuario(username,password_hash,rol) VALUES ('nuevo','hash','ADMIN')");
                try (var filas = sql.executeQuery("SELECT idioma FROM usuario ORDER BY id")) {
                    assertThat(filas.next()).isTrue();
                    assertThat(filas.getString(1)).isEqualTo("es");
                    assertThat(filas.next()).isTrue();
                    assertThat(filas.getString(1)).isEqualTo("es");
                }
                var savepoint = connection.setSavepoint();
                assertThatThrownBy(() -> sql.execute("UPDATE usuario SET idioma='xx'"))
                        .isInstanceOf(SQLException.class);
                connection.rollback(savepoint);
                assertThatThrownBy(() -> sql.execute("UPDATE usuario SET idioma=NULL"))
                        .isInstanceOf(SQLException.class);
            } finally {
                connection.rollback();
                connection.setAutoCommit(true);
            }
        }
    }
}
