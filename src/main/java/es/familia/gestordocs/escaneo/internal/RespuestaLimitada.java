package es.familia.gestordocs.escaneo.internal;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Limita también respuestas chunked, antes de acumularlas completamente en memoria. */
final class RespuestaLimitada implements HttpResponse.BodySubscriber<byte[]> {
    private final HttpResponse.BodySubscriber<byte[]> destino = HttpResponse.BodySubscribers.ofByteArray();
    private final long limite;
    private long recibidos;
    private Flow.Subscription suscripcion;
    private boolean terminada;

    RespuestaLimitada(long limite) {
        this.limite = limite;
    }

    @Override
    public CompletionStage<byte[]> getBody() {
        return destino.getBody();
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        suscripcion = subscription;
        destino.onSubscribe(subscription);
    }

    @Override
    public void onNext(List<ByteBuffer> bloques) {
        if (terminada) {
            return;
        }
        recibidos += bloques.stream().mapToLong(ByteBuffer::remaining).sum();
        if (recibidos > limite) {
            terminada = true;
            suscripcion.cancel();
            destino.onError(new RespuestaDemasiadoGrande());
        } else {
            destino.onNext(bloques);
        }
    }

    @Override
    public void onError(Throwable error) {
        if (!terminada) {
            terminada = true;
            destino.onError(error);
        }
    }

    @Override
    public void onComplete() {
        if (!terminada) {
            terminada = true;
            destino.onComplete();
        }
    }

    static final class RespuestaDemasiadoGrande extends IOException {
    }
}
