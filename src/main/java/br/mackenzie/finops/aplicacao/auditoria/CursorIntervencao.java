package br.mackenzie.finops.aplicacao.auditoria;

import br.mackenzie.finops.aplicacao.excecoes.EntradaRequisicaoInvalidaException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

public record CursorIntervencao(Instant instante, UUID operacaoId) {
    public String codificar() {
        String texto = instante + "|" + operacaoId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(texto.getBytes(StandardCharsets.UTF_8));
    }

    public static CursorIntervencao ler(String texto) {
        if (texto == null) {
            return null;
        }
        try {
            if (texto.length() > 128) {
                throw new IllegalArgumentException();
            }
            String[] partes = new String(Base64.getUrlDecoder().decode(texto), StandardCharsets.UTF_8).split("\\|", -1);
            if (partes.length != 2) {
                throw new IllegalArgumentException();
            }
            var cursor = new CursorIntervencao(Instant.parse(partes[0]), UUID.fromString(partes[1]));
            int ano = cursor.instante().atOffset(java.time.ZoneOffset.UTC).getYear();
            if (ano < 1000 || ano > 9999 || !cursor.instante().equals(cursor.instante().truncatedTo(ChronoUnit.MICROS))
                    || !texto.equals(cursor.codificar())) {
                throw new IllegalArgumentException();
            }
            return cursor;
        } catch (IllegalArgumentException | java.time.DateTimeException falha) {
            throw new EntradaRequisicaoInvalidaException();
        }
    }
}
