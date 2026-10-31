package es.familia.gestordocs.documentos.internal;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "documento")
public class Documento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_visible", nullable = false)
    private String nombreVisible;

    @Column(name = "nombre_original", nullable = false, updatable = false)
    private String nombreOriginal;

    @Column(name = "tipo_mime", nullable = false, updatable = false, length = 150)
    private String tipoMime;

    @Column(name = "tamano_bytes", nullable = false, updatable = false)
    private long tamanoBytes;

    @Column(name = "clave_fichero", nullable = false, updatable = false)
    private UUID claveFichero;

    @Column(name = "clave_miniatura", updatable = false)
    private UUID claveMiniatura;

    private String descripcion;

    /** Persona de otro módulo: solo guardamos su id (sin relación JPA). */
    @Column(name = "persona_id")
    private Long personaId;

    @Column(name = "subido_por_id", nullable = false, updatable = false)
    private Long subidoPorId;

    @Column(name = "subido_en", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime subidoEn;

    /** Secciones de otro módulo: un conjunto de ids en la tabla documento_seccion. */
    @ElementCollection
    @CollectionTable(name = "documento_seccion", joinColumns = @JoinColumn(name = "documento_id"))
    @Column(name = "seccion_id")
    @BatchSize(size = 100)
    private Set<Long> seccionIds = new HashSet<>();

    protected Documento() {
        // para JPA
    }

    public Documento(String nombreVisible, String nombreOriginal, String tipoMime, long tamanoBytes,
            UUID claveFichero, UUID claveMiniatura, Long subidoPorId) {
        this.nombreVisible = nombreVisible;
        this.nombreOriginal = nombreOriginal;
        this.tipoMime = tipoMime;
        this.tamanoBytes = tamanoBytes;
        this.claveFichero = claveFichero;
        this.claveMiniatura = claveMiniatura;
        this.subidoPorId = subidoPorId;
        // Hasta que se recargue desde la BD (DEFAULT now()), para que el DTO no lleve null.
        this.subidoEn = OffsetDateTime.now();
    }

    public void actualizar(String nombreVisible, String descripcion, Long personaId, Collection<Long> seccionIds) {
        this.nombreVisible = nombreVisible.trim();
        this.descripcion = descripcion == null || descripcion.isBlank() ? null : descripcion.trim();
        this.personaId = personaId;
        this.seccionIds.clear();
        this.seccionIds.addAll(seccionIds);
    }

    public Long getId() {
        return id;
    }

    public String getNombreVisible() {
        return nombreVisible;
    }

    public String getNombreOriginal() {
        return nombreOriginal;
    }

    public String getTipoMime() {
        return tipoMime;
    }

    public long getTamanoBytes() {
        return tamanoBytes;
    }

    public UUID getClaveFichero() {
        return claveFichero;
    }

    public UUID getClaveMiniatura() {
        return claveMiniatura;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public Long getPersonaId() {
        return personaId;
    }

    public Long getSubidoPorId() {
        return subidoPorId;
    }

    public OffsetDateTime getSubidoEn() {
        return subidoEn;
    }

    public Set<Long> getSeccionIds() {
        return Set.copyOf(seccionIds);
    }
}
