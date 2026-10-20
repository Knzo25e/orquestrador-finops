package br.mackenzie.finops.infraestrutura.persistencia;

import java.math.BigDecimal;
import java.util.UUID;

public final class VerificarValidacaoCadastroJdbc {
    private static int verificacoes;

    public static void main(String[] args) {
        System.out.println("--- Limites dos cadastros JDBC / Issue #61 ---");

        UUID id = UUID.randomUUID();

        conferir(
                ValidacaoCadastroJdbc.validarId(id, "Identificador").equals(id),
                "Preserva UUID valido"
        );

        rejeitar(
                () -> ValidacaoCadastroJdbc.validarId(null, "Identificador"),
                "Rejeita UUID nulo"
        );

        conferir(
                ValidacaoCadastroJdbc.interpretarId(id.toString()).equals(id),
                "Recupera UUID canonico"
        );

        for (String texto : new String[]{
                null, "", "invalido", "1-1-1-1-1",
                "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA"
        }) {
            rejeitar(
                    () -> ValidacaoCadastroJdbc.interpretarId(texto),
                    "Rejeita UUID armazenado invalido ou nao canonico"
            );
        }

        conferir(
                ValidacaoCadastroJdbc.validarNome("  Engenharia  ")
                        .equals("  Engenharia  "),
                "Preserva espacos de um nome valido"
        );

        for (String nome : new String[]{null, "", "   ", "A".repeat(256)}) {
            rejeitar(
                    () -> ValidacaoCadastroJdbc.validarNome(nome),
                    "Rejeita nome ausente ou acima do limite"
            );
        }

        conferir(
                ValidacaoCadastroJdbc.validarNome("A".repeat(255))
                        .length() == 255,
                "Aceita nome com 255 caracteres"
        );

        String simbolo = new String(Character.toChars(0x1F680));
        String nomeUnicode = simbolo.repeat(255);

        conferir(
                ValidacaoCadastroJdbc.validarNome(nomeUnicode)
                        .equals(nomeUnicode),
                "Limite considera pontos de codigo Unicode"
        );

        rejeitar(
                () -> ValidacaoCadastroJdbc.validarNome(simbolo.repeat(256)),
                "Rejeita 256 pontos de codigo Unicode"
        );

        conferir(
                moeda("0").equals(new BigDecimal("0.00")),
                "Aceita e normaliza zero"
        );

        conferir(
                moeda("1.2300").equals(new BigDecimal("1.23")),
                "Aceita zeros decimais adicionais"
        );

        conferir(
                moeda("99999999999999999.99")
                        .equals(new BigDecimal("99999999999999999.99")),
                "Aceita limite superior de DECIMAL(19,2)"
        );

        rejeitar(
                () -> ValidacaoCadastroJdbc.validarValorMonetario(
                        null, "Valor"
                ),
                "Rejeita valor nulo"
        );

        for (String valor : new String[]{
                "-0.01", "0.001", "100000000000000000.00"
        }) {
            rejeitar(
                    () -> moeda(valor),
                    "Rejeita valor negativo, fracao de centavo ou excesso"
            );
        }

        ValidacaoCadastroJdbc.validarSaldo(moeda("100"), moeda("100"));
        conferir(true, "Aceita saldo igual ao teto");

        ValidacaoCadastroJdbc.validarSaldo(moeda("100"), moeda("0"));
        conferir(true, "Aceita saldo zero");

        rejeitar(
                () -> ValidacaoCadastroJdbc.validarSaldo(
                        moeda("100"), moeda("100.01")
                ),
                "Rejeita saldo superior ao teto"
        );

        rejeitar(
                () -> new DepartamentoRepositoryJdbc(null),
                "Repositorio de departamentos rejeita conexao nula"
        );

        rejeitar(
                () -> new RecursoCatalogoRepositoryJdbc(null),
                "Repositorio de recursos rejeita conexao nula"
        );

        System.out.println(
                "Todas as " + verificacoes + " verificacoes passaram."
        );
    }

    private static BigDecimal moeda(String texto) {
        return ValidacaoCadastroJdbc.validarValorMonetario(
                new BigDecimal(texto), "Valor"
        );
    }

    private static void rejeitar(Runnable operacao, String descricao) {
        try {
            operacao.run();
        } catch (IllegalArgumentException excecao) {
            conferir(
                    excecao.getMessage() != null
                            && !excecao.getMessage().isBlank(),
                    descricao
            );
            return;
        }

        throw new IllegalStateException("FALHOU: " + descricao);
    }

    private static void conferir(boolean condicao, String descricao) {
        if (!condicao) {
            throw new IllegalStateException("FALHOU: " + descricao);
        }

        verificacoes++;
        System.out.println("PASSOU: " + descricao);
    }
}