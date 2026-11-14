package es.familia.gestordocs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class I18nCatalogosTest {
    private Properties cargar(String nombre) throws Exception {
        var properties = new Properties();
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/" + nombre), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    @Test
    void catalogosCompletosConParametrosCompatiblesYPatronesValidos() throws Exception {
        var es = cargar("messages.properties");
        var en = cargar("messages_en.properties");
        assertThat(en.stringPropertyNames()).isEqualTo(es.stringPropertyNames());
        for (var clave : es.stringPropertyNames()) {
            var a = es.getProperty(clave);
            var b = en.getProperty(clave);
            assertThat(a).as(clave).isNotBlank();
            assertThat(b).as(clave).isNotBlank();
            assertThat(parametros(b)).as(clave).isEqualTo(parametros(a));
            if (a.matches(".*\\{\\d+.*")) {
                new MessageFormat(a, Locale.forLanguageTag("es"));
                new MessageFormat(b, Locale.ENGLISH);
            }
        }
    }

    @Test
    void todasLasClavesDeLasPlantillasExisten() throws Exception {
        var catalogo = cargar("messages.properties");
        try (var archivos = java.nio.file.Files.walk(java.nio.file.Path.of("src/main/resources/templates"))) {
            for (var archivo : archivos.filter(p -> p.toString().endsWith(".html")).toList()) {
                var texto = java.nio.file.Files.readString(archivo);
                var matcher = Pattern.compile("#\\{([\\w.]+)").matcher(texto);
                while (matcher.find()) {
                    assertThat(catalogo).as(archivo + ": " + matcher.group(1)).containsKey(matcher.group(1));
                }
            }
        }
    }

    private Set<String> parametros(String texto) {
        var matcher = Pattern.compile("\\{(\\w+)(?:[,}])").matcher(texto);
        var parametros = new HashSet<String>();
        while (matcher.find()) parametros.add(matcher.group(1));
        return parametros;
    }
}
