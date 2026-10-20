package br.mackenzie.finops.infraestrutura.persistencia;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Limites de representação dos cadastros no esquema MySQL.
 */
final class ValidacaoCadastroJdbc {

    private static final BigDecimal VALOR_MAXIMO =
            new BigDecimal("99999999999999999.99");

    private ValidacaoCadastroJdbc() {
    }

    static UUID validarId(UUID id, String campo) {
        if (id == null) {
            throw new IllegalArgumentException(
                    campo + " é obrigatório."
            );
        }

        return id;
    }

    static String validarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException(
                    "O nome é obrigatório e não pode estar em branco."
            );
        }

        if (nome.codePointCount(0, nome.length()) > 255) {
            throw new IllegalArgumentException(
                    "O nome não pode ultrapassar 255 caracteres Unicode."
            );
        }

        return nome;
    }

    static BigDecimal validarValorMonetario(
            BigDecimal valor,
            String campo
    ) {
        if (valor == null) {
            throw new IllegalArgumentException(
                    campo + " é obrigatório."
            );
        }

        if (valor.signum() < 0 || valor.compareTo(VALOR_MAXIMO) > 0) {
            throw new IllegalArgumentException(
                    campo + " deve estar entre 0.00 e "
                            + VALOR_MAXIMO.toPlainString() + "."
            );
        }

        try {
            return valor.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException excecao) {
            throw new IllegalArgumentException(
                    campo + " não pode conter frações de centavo."
            );
        }
    }

    static void validarSaldo(
            BigDecimal teto,
            BigDecimal saldo
    ) {
        if (saldo.compareTo(teto) > 0) {
            throw new IllegalArgumentException(
                    "O saldo disponível não pode ser superior ao teto."
            );
        }
    }

    static UUID interpretarId(String texto) {
        if (texto == null) {
            throw new IllegalArgumentException(
                    "O identificador armazenado é obrigatório."
            );
        }

        UUID id;

        try {
            id = UUID.fromString(texto);
        } catch (IllegalArgumentException excecao) {
            throw new IllegalArgumentException(
                    "O identificador armazenado não é um UUID válido."
            );
        }

        if (!id.toString().equals(texto)) {
            throw new IllegalArgumentException(
                    "O UUID armazenado deve utilizar a representação canônica."
            );
        }

        return id;
    }
}