package br.mackenzie.finops.aplicacao.portas;

import br.mackenzie.finops.aplicacao.auditoria.CursorIntervencao;
import br.mackenzie.finops.aplicacao.auditoria.EventoIntervencao;
import java.util.List;
import java.util.UUID;

public interface ConsultaIntervencoesPersistente {
    List<EventoIntervencao> listarAutorizado(
            UUID usuario, UUID departamento, UUID requisicao, CursorIntervencao apos, int limite
    );
}
