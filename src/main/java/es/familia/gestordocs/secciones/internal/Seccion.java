package es.familia.gestordocs.secciones.internal;

import java.time.OffsetDateTime;

import es.familia.gestordocs.secciones.SeccionDto;
import es.familia.gestordocs.secciones.SeccionForm;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "seccion")
public class Seccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    private String descripcion;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime creadoEn;

    protected Seccion() {
        // para JPA
    }

    public Seccion(SeccionForm datos) {
        actualizar(datos);
    }

    public void actualizar(SeccionForm datos) {
        this.nombre = datos.nombre().trim();
        this.descripcion = datos.descripcion() == null || datos.descripcion().isBlank()
                ? null
                : datos.descripcion().trim();
    }

    public SeccionDto toDto() {
        return new SeccionDto(id, nombre, descripcion);
    }
}
