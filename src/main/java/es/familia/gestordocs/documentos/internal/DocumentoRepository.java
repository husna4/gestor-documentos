package es.familia.gestordocs.documentos.internal;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DocumentoRepository extends JpaRepository<Documento, Long> {

    List<Documento> findByOrderBySubidoEnDesc(Limit limite);

    List<Documento> findByPersonaIdOrderByNombreVisibleAsc(Long personaId);

    long countByPersonaId(Long personaId);

    @Query("select d from Documento d join d.seccionIds s where s = :seccionId order by d.nombreVisible")
    List<Documento> buscarPorSeccion(Long seccionId);

    @Query("""
            select d from Documento d join d.seccionIds s
            where s = :seccionId and d.personaId = :personaId
            order by d.nombreVisible""")
    List<Documento> buscarPorSeccionYPersona(Long seccionId, Long personaId);

    @Query("select d from Documento d where d.seccionIds is empty order by d.nombreVisible")
    List<Documento> buscarSinClasificar();

    @Query("select count(d) from Documento d join d.seccionIds s where s = :seccionId")
    long contarPorSeccion(Long seccionId);

    /** Pares [seccionId, número de documentos], para las tarjetas del inicio. */
    @Query("select s, count(d) from Documento d join d.seccionIds s group by s")
    List<Object[]> contarPorCadaSeccion();

    @Query("select count(d) from Documento d where d.seccionIds is empty")
    long contarSinClasificar();

    /**
     * Búsqueda por nombre sin distinguir mayúsculas ni tildes, con coincidencia parcial.
     * El patrón llega ya escapado ({@code %} y {@code _} literales). Usa el índice trigram de V4.
     */
    @Query(value = """
            select * from documento d
            where f_unaccent(lower(d.nombre_visible)) like '%' || f_unaccent(lower(:texto)) || '%'
            order by d.subido_en desc
            limit 50""", nativeQuery = true)
    List<Documento> buscarPorNombre(String texto);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Documento d set d.personaId = null where d.personaId = :personaId")
    int quitarPersona(Long personaId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "delete from documento_seccion where seccion_id = :seccionId", nativeQuery = true)
    int quitarSeccion(Long seccionId);
}
