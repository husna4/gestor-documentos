package es.familia.gestordocs.personas.internal;

import java.time.OffsetDateTime;

import es.familia.gestordocs.personas.PersonaDto;
import es.familia.gestordocs.personas.PersonaForm;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "persona")
public class Persona {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 150)
    private String apellidos;

    private String notas;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime creadoEn;

    protected Persona() {
        // para JPA
    }

    public Persona(PersonaForm datos) {
        actualizar(datos);
    }

    public void actualizar(PersonaForm datos) {
        this.nombre = datos.nombre().trim();
        this.apellidos = textoOpcional(datos.apellidos());
        this.notas = textoOpcional(datos.notas());
    }

    public PersonaDto toDto() {
        return new PersonaDto(id, nombre, apellidos, notas);
    }

    private static String textoOpcional(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
