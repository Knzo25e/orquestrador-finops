package br.mackenzie.finops.dominio.entidades;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.math.RoundingMode;

public class Requisicao {
    private final UUID id;
    private final Departamento departamento;
    private final UUID desenvolvedorId;
    private String status;
    private BigDecimal custoTotalProjetado;
    private String justificativaArquiteto;
    private final List<ItemRequisicao> itens;

    private ResultadoValidacao motivoRevisao;

    public Requisicao(Departamento departamento) {
        if (departamento == null) {
            throw new IllegalArgumentException(
                    "Uma requisição não pode ser criada sem um departamento solicitante."
            );
        }

        this.id = UUID.randomUUID();
        this.departamento = departamento;
        this.desenvolvedorId = null;
        this.status = "INICIAL";
        this.custoTotalProjetado = new BigDecimal("0.00");
        this.itens = new ArrayList<>();
    }

    // Compatibilidade com os caminhos anteriores, sem autoria conhecida.
    private Requisicao(
            UUID id,
            Departamento departamento,
            List<ItemRequisicao> itens,
            String status,
            BigDecimal custoTotalProjetado,
            ResultadoValidacao motivoRevisao
    ) {
        this(
                id,
                departamento,
                itens,
                status,
                custoTotalProjetado,
                motivoRevisao,
                null
        );
    }

    private Requisicao(
            UUID id,
            Departamento departamento,
            List<ItemRequisicao> itens,
            String status,
            BigDecimal custoTotalProjetado,
            ResultadoValidacao motivoRevisao,
            UUID desenvolvedorId
    ) {
        this.id = id;
        this.departamento = departamento;
        this.desenvolvedorId = desenvolvedorId;
        this.itens = new ArrayList<>(itens);
        this.status = status;
        this.custoTotalProjetado = custoTotalProjetado;
        this.motivoRevisao = motivoRevisao;
    }

    public static Requisicao criarComId(
            UUID id,
            Departamento departamento
    ) {
        validarIdentidade(id, departamento);

        return new Requisicao(
                id,
                departamento,
                List.of(),
                "INICIAL",
                new BigDecimal("0.00"),
                null
        );
    }

    /**
     * Cria uma requisicao com autoria informada pela aplicacao.
     * O identificador do autor nao constitui autenticacao.
     */
    public static Requisicao criarComId(
            UUID id,
            Departamento departamento,
            UUID desenvolvedorId
    ) {
        validarIdentidade(id, departamento);

        if (desenvolvedorId == null) {
            throw new IllegalArgumentException(
                    "O identificador do desenvolvedor e obrigatorio."
            );
        }

        return new Requisicao(
                id,
                departamento,
                List.of(),
                "INICIAL",
                new BigDecimal("0.00"),
                null,
                desenvolvedorId
        );
    }


       // Compatibilidade com registros sem autoria conhecida.
    public static Requisicao reconstituir(
            UUID id,
            Departamento departamento,
            List<ItemRequisicao> itens,
            String status,
            BigDecimal totalArmazenado,
            ResultadoValidacao motivoRevisao
    ) {
        return reconstituir(
                id,
                departamento,
                itens,
                status,
                totalArmazenado,
                motivoRevisao,
                null
        );
    }

    /**
     * Recupera a autoria armazenada.
     * Autor nulo representa registro sem autoria conhecida.
     */
    public static Requisicao reconstituir(
            UUID id,
            Departamento departamento,
            List<ItemRequisicao> itens,
            String status,
            BigDecimal totalArmazenado,
            ResultadoValidacao motivoRevisao,
            UUID desenvolvedorId
    ) {
        validarIdentidade(id, departamento);
        validarEstadoEMotivo(status, motivoRevisao);

        if (itens == null) {
            throw new IllegalArgumentException(
                    "A lista de itens é obrigatória."
            );
        }

        List<ItemRequisicao> copia = new ArrayList<>(itens);

        if (copia.isEmpty()) {
            throw new IllegalArgumentException(
                    "A requisição reconstituída deve conter pelo menos um item."
            );
        }

        BigDecimal soma = new BigDecimal("0.00");

        for (ItemRequisicao item : copia) {
            if (item == null) {
                throw new IllegalArgumentException(
                        "A lista não pode conter um item nulo."
                );
            }

            if (item.getRecurso() == null
                    || item.getRecurso().getId() == null) {
                throw new IllegalArgumentException(
                        "Cada item deve possuir um recurso com identificador."
                );
            }

            soma = soma.add(item.getCustoSubtotal());
        }

        BigDecimal totalValidado = validarTotalArmazenado(
                totalArmazenado
        );

        if (totalValidado.compareTo(soma) != 0) {
            throw new IllegalArgumentException(
                    "O total armazenado deve ser igual à soma dos subtotais."
            );
        }

        return new Requisicao(
                id,
                departamento,
                copia,
                status,
                totalValidado,
                motivoRevisao,
                desenvolvedorId
        );
    }


    private static void validarIdentidade(
            UUID id,
            Departamento departamento
    ) {
        if (id == null) {
            throw new IllegalArgumentException(
                    "O identificador da requisição é obrigatório."
            );
        }

        if (departamento == null || departamento.getId() == null) {
            throw new IllegalArgumentException(
                    "A requisição deve possuir um departamento com identificador."
            );
        }
    }

    private static void validarEstadoEMotivo(
            String status,
            ResultadoValidacao motivoRevisao
    ) {
        EstadoRequisicao estado = EstadoRequisicao.deStatus(status);
        if (estado == EstadoRequisicao.REVISAO_PENDENTE) {
            if (motivoRevisao
                    != ResultadoValidacao.REVISAO_POR_SALDO_INSUFICIENTE
                    && motivoRevisao
                    != ResultadoValidacao.REVISAO_POR_RESERVA) {
                throw new IllegalArgumentException(
                        "Uma requisição em revisão deve possuir um motivo válido."
                );
            }
        } else if (motivoRevisao != null) {
            throw new IllegalArgumentException(
                    "Somente uma requisição em revisão pode possuir motivo de revisão."
            );
        }
    }

    private static BigDecimal validarTotalArmazenado(
            BigDecimal total
    ) {
        if (total == null) {
            throw new IllegalArgumentException(
                    "O total armazenado é obrigatório."
            );
        }

        if (total.signum() < 0) {
            throw new IllegalArgumentException(
                    "O total armazenado não pode ser negativo."
            );
        }

        try {
            return total.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException excecao) {
            throw new IllegalArgumentException(
                    "O total armazenado não pode conter frações de centavo.",
                    excecao
            );
        }
    }

    public void adicionarItem(ItemRequisicao item) {
        synchronized (departamento) {
            if (item == null) {
                throw new IllegalArgumentException(
                        "O item da requisição é obrigatório."
                );
            }

            if (!"INICIAL".equals(status)) {
                throw new IllegalStateException(
                        "Só é permitido adicionar itens a uma requisição em INICIAL."
                );
            }

            itens.add(item);
            calcularTotalProjetado();
        }
    }

    public BigDecimal calcularTotalProjetado() {
        synchronized (departamento) {
            BigDecimal total = new BigDecimal("0.00");

            for (ItemRequisicao item : itens) {
                total = total.add(item.getCustoSubtotal());
            }

            custoTotalProjetado = total;
            return total;
        }
    }

    public ResultadoValidacao processarRoteamento() {
        synchronized (departamento) {
            if ("Provisionamento Liberado".equals(status)) {
                return ResultadoValidacao.JA_LIBERADA;
            }

            if (!"INICIAL".equals(status)) {
                return ResultadoValidacao.ESTADO_NAO_PERMITIDO;
            }

            if (itens.isEmpty()) {
                throw new IllegalArgumentException(
                        "A requisição deve conter pelo menos um item."
                );
            }

            ResultadoValidacao condicao = avaliarCondicaoFinanceira();
            status = "Em Analise";
            if (condicao != ResultadoValidacao.LIBERADA) {
                return enviarParaRevisao(condicao);
            }
            liberarComDebito();
            return ResultadoValidacao.LIBERADA;
        }
    }

    public ResultadoIntervencaoManual revisarTecnicamente(DecisaoManual decisao) {
        synchronized (departamento) {
            if (decisao == null || !decisao.tecnica()) {
                throw new IllegalArgumentException("Decisão técnica inválida.");
            }
            if (!"Revisão Pendente".equals(status)) {
                return ResultadoIntervencaoManual.ESTADO_NAO_PERMITIDO;
            }
            if (decisao == DecisaoManual.REJEITAR) {
                status = EstadoRequisicao.REJEITADO.statusDominio();
                motivoRevisao = null;
                return ResultadoIntervencaoManual.REJEITADA;
            }
            status = switch (avaliarCondicaoFinanceira()) {
                case REVISAO_POR_SALDO_INSUFICIENTE -> EstadoRequisicao.AGUARDANDO_AJUSTE_ORCAMENTARIO.statusDominio();
                case REVISAO_POR_RESERVA -> EstadoRequisicao.AGUARDANDO_AUTORIZACAO_FINANCEIRA.statusDominio();
                case LIBERADA -> EstadoRequisicao.AGUARDANDO_LIBERACAO_FINANCEIRA.statusDominio();
                default -> throw new IllegalStateException("Avaliação financeira inconsistente.");
            };
            motivoRevisao = null;
            return ResultadoIntervencaoManual.APROVADA_TECNICAMENTE;
        }
    }

    public ResultadoIntervencaoManual liberarFinanceiramente(DecisaoManual decisao) {
        synchronized (departamento) {
            if (decisao == null || decisao.tecnica()) {
                throw new IllegalArgumentException("Decisão financeira inválida.");
            }
            var estado = EstadoRequisicao.deStatus(status);
            if (estado != EstadoRequisicao.AGUARDANDO_AJUSTE_ORCAMENTARIO
                    && estado != EstadoRequisicao.AGUARDANDO_AUTORIZACAO_FINANCEIRA
                    && estado != EstadoRequisicao.AGUARDANDO_LIBERACAO_FINANCEIRA) {
                return ResultadoIntervencaoManual.ESTADO_NAO_PERMITIDO;
            }
            var condicao = avaliarCondicaoFinanceira();
            if (condicao == ResultadoValidacao.REVISAO_POR_SALDO_INSUFICIENTE) {
                status = EstadoRequisicao.AGUARDANDO_AJUSTE_ORCAMENTARIO.statusDominio();
                return ResultadoIntervencaoManual.AGUARDA_AJUSTE;
            }
            if (condicao == ResultadoValidacao.REVISAO_POR_RESERVA) {
                if (decisao == DecisaoManual.AUTORIZAR_RESERVA) {
                    liberarComDebito();
                    return ResultadoIntervencaoManual.LIBERADA_COM_RESERVA;
                }
                status = EstadoRequisicao.AGUARDANDO_AUTORIZACAO_FINANCEIRA.statusDominio();
                return decisao == DecisaoManual.NEGAR_RESERVA
                        ? ResultadoIntervencaoManual.RESERVA_NEGADA : ResultadoIntervencaoManual.AGUARDA_AUTORIZACAO;
            }
            if (decisao == DecisaoManual.LIBERAR_SEM_RESERVA) {
                liberarComDebito();
                return ResultadoIntervencaoManual.LIBERADA;
            }
            status = EstadoRequisicao.AGUARDANDO_LIBERACAO_FINANCEIRA.statusDominio();
            return ResultadoIntervencaoManual.RESERVA_DISPENSADA;
        }
    }

    private ResultadoValidacao avaliarCondicaoFinanceira() {
        BigDecimal custo = calcularTotalProjetado();
        BigDecimal saldo = departamento.getSaldoDisponivel();
        if (custo.compareTo(saldo) > 0) {
            return ResultadoValidacao.REVISAO_POR_SALDO_INSUFICIENTE;
        }
        if (saldo.subtract(custo).compareTo(departamento.calcularReserva()) <= 0) {
            return ResultadoValidacao.REVISAO_POR_RESERVA;
        }
        return ResultadoValidacao.LIBERADA;
    }

    private void liberarComDebito() {
        departamento.descontarSaldo(custoTotalProjetado);
        status = "Provisionamento Liberado";
        motivoRevisao = null;
    }

    // Chamado somente dentro da operação coordenada.
    private ResultadoValidacao enviarParaRevisao(
            ResultadoValidacao motivo
    ) {
        motivoRevisao = motivo;
        status = "Revisão Pendente";
        return motivo;
    }

    public UUID getId() {
        return id;
    }

    /**
     * Retorna o autor ou null quando a autoria nao e conhecida.
     * A ausencia de autoria nao deve ser preenchida por suposicao.
     */
    public UUID getDesenvolvedorId() {
        return desenvolvedorId;
    }

    public Departamento getDepartamento() {
        return departamento;
    }

    public String getStatus() {
        synchronized (departamento) {
            return status;
        }
    }

    public BigDecimal getCustoTotalProjetado() {
        synchronized (departamento) {
            return custoTotalProjetado;
        }
    }

    public ResultadoValidacao getMotivoRevisao() {
        synchronized (departamento) {
            return motivoRevisao;
        }
    }

    public List<ItemRequisicao> getItens() {
        synchronized (departamento) {
            return List.copyOf(itens);
        }
    }
}
