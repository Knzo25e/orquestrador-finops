package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.Requisicao;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cópia validada dos dados necessários ao cadastro.
 * Não executa SQL nem altera a requisição.
 */
final class DadosCadastroRequisicaoJdbc {

    record Item(
            UUID recursoId,
            int quantidade,
            BigDecimal precoUnitario,
            BigDecimal subtotal
    ) {
    }

    private final UUID id;
    private final UUID departamentoId;
    private final BigDecimal total;
    private final List<Item> itens;

    private DadosCadastroRequisicaoJdbc(
            UUID id,
            UUID departamentoId,
            BigDecimal total,
            List<Item> itens
    ) {
        this.id = id;
        this.departamentoId = departamentoId;
        this.total = total;
        this.itens = List.copyOf(itens);
    }

    static DadosCadastroRequisicaoJdbc preparar(Requisicao requisicao) {
        if (requisicao == null) {
            throw new IllegalArgumentException(
                    "A requisição é obrigatória."
            );
        }

        Departamento departamento = requisicao.getDepartamento();

        if (departamento == null) {
            throw new IllegalArgumentException(
                    "O departamento da requisição é obrigatório."
            );
        }

        synchronized (departamento) {
            UUID id = ValidacaoCadastroJdbc.validarId(
                    requisicao.getId(),
                    "O identificador da requisição"
            );

            UUID departamentoId = ValidacaoCadastroJdbc.validarId(
                    departamento.getId(),
                    "O identificador do departamento"
            );

            if (!"INICIAL".equals(requisicao.getStatus())
                    || requisicao.getMotivoRevisao() != null) {
                throw new IllegalArgumentException(
                        "O cadastro exige requisição em INICIAL e sem motivo de revisão."
                );
            }

            List<ItemRequisicao> itensOriginais = requisicao.getItens();

            if (itensOriginais.isEmpty()) {
                throw new IllegalArgumentException(
                        "A requisição deve conter pelo menos um item."
                );
            }

            List<Item> itens = new ArrayList<>();
            BigDecimal soma = new BigDecimal("0.00");

            for (ItemRequisicao item : itensOriginais) {
                Item copia = prepararItem(item);
                itens.add(copia);

                soma = ValidacaoCadastroJdbc.validarValorMonetario(
                        soma.add(copia.subtotal()),
                        "A soma dos subtotais"
                );
            }

            BigDecimal total = ValidacaoCadastroJdbc.validarValorMonetario(
                    requisicao.getCustoTotalProjetado(),
                    "O total da requisição"
            );

            if (total.compareTo(soma) != 0) {
                throw new IllegalArgumentException(
                        "O total da requisição deve ser igual à soma dos subtotais."
                );
            }

            return new DadosCadastroRequisicaoJdbc(
                    id,
                    departamentoId,
                    total,
                    itens
            );
        }
    }

    private static Item prepararItem(ItemRequisicao item) {
        if (item == null || item.getRecurso() == null) {
            throw new IllegalArgumentException(
                    "O item e seu recurso são obrigatórios."
            );
        }

        UUID recursoId = ValidacaoCadastroJdbc.validarId(
                item.getRecurso().getId(),
                "O identificador do recurso"
        );

        Integer quantidade = item.getQuantidade();

        if (quantidade == null || quantidade <= 0) {
            throw new IllegalArgumentException(
                    "A quantidade do item deve ser maior que zero."
            );
        }

        BigDecimal preco = ValidacaoCadastroJdbc.validarValorMonetario(
                item.getPrecoUnitario(),
                "O preço unitário"
        );

        BigDecimal subtotal = ValidacaoCadastroJdbc.validarValorMonetario(
                item.getCustoSubtotal(),
                "O subtotal"
        );

        BigDecimal subtotalCalculado =
                ValidacaoCadastroJdbc.validarValorMonetario(
                        preco.multiply(BigDecimal.valueOf(quantidade)),
                        "O subtotal calculado"
                );

        if (subtotal.compareTo(subtotalCalculado) != 0) {
            throw new IllegalArgumentException(
                    "O subtotal deve ser igual ao preço unitário multiplicado pela quantidade."
            );
        }

        return new Item(recursoId, quantidade, preco, subtotal);
    }

    UUID id() {
        return id;
    }

    UUID departamentoId() {
        return departamentoId;
    }

    BigDecimal total() {
        return total;
    }

    List<Item> itens() {
        return itens;
    }
}