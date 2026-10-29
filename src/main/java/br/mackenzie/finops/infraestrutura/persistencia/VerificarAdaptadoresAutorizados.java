package br.mackenzie.finops.infraestrutura.persistencia;

import br.mackenzie.finops.aplicacao.ConsultarRequisicaoAutorizadaUseCase;
import br.mackenzie.finops.aplicacao.CriarRequisicaoCoordenadaUseCase;
import br.mackenzie.finops.aplicacao.ListarRequisicoesAutorizadasUseCase;
import br.mackenzie.finops.aplicacao.entradas.ItemSolicitado;
import br.mackenzie.finops.aplicacao.excecoes.AcessoNegadoException;
import br.mackenzie.finops.aplicacao.portas.ConsultaRequisicaoPersistente;
import br.mackenzie.finops.aplicacao.portas.CriacaoRequisicaoAutorizadaPersistente;
import br.mackenzie.finops.aplicacao.resultados.ResultadoCriacaoRequisicao;
import br.mackenzie.finops.aplicacao.seguranca.ContextoIdentidade;
import br.mackenzie.finops.aplicacao.seguranca.PerfilUsuario;
import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.ItemRequisicao;
import br.mackenzie.finops.dominio.entidades.RecursoCatalogo;
import br.mackenzie.finops.dominio.entidades.Requisicao;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class VerificarAdaptadoresAutorizados {

    private static int verificacoes;

    public static void main(String[] args) {
        executar(false);
        executar(true);

        System.out.println(
                "Todas as " + verificacoes + " verificacoes passaram."
        );
    }

    private static void executar(boolean textual) {
        String nome = textual ? "Adaptador textual" : "Adaptador tipado";

        UUID usuario = UUID.randomUUID();
        UUID pedidoId = UUID.randomUUID();

        Departamento departamento = Departamento.reconstituir(
                UUID.randomUUID(),
                "Departamento dos adaptadores",
                new BigDecimal("1000.00"),
                new BigDecimal("800.00")
        );

        RecursoCatalogo recurso = new RecursoCatalogo(
                UUID.randomUUID(),
                "Recurso dos adaptadores",
                new BigDecimal("12.50")
        );

        IdentidadeCoordenadaTeste fonte = new IdentidadeCoordenadaTeste(
                usuario,
                contexto(usuario, departamento.getId(), true)
        );

        Requisicao proprio = pedido(
                pedidoId, departamento, recurso, usuario
        );

        ConsultaControlada consultas = new ConsultaControlada();
        consultas.resposta = proprio;

        int[] criacoes = {0};

        CriacaoRequisicaoAutorizadaPersistente persistencia =
                (solicitacao, identidade) -> {
                    criacoes[0]++;

                    // Confere o que chegou à porta, sem decidir autorização.
                    if (!usuario.equals(solicitacao.desenvolvedorId())
                            || !departamento.getId().equals(
                                    solicitacao.departamentoId()
                            )
                            || !pedidoId.equals(solicitacao.requisicaoId())
                            || solicitacao.itens().size() != 1
                            || !recurso.getId().equals(
                                    solicitacao.itens().get(0).recursoId()
                            )
                            || solicitacao.itens().get(0).quantidade() != 2) {
                        throw new AssertionError(
                                "O adaptador alterou os dados encaminhados."
                        );
                    }

                    return ResultadoCriacaoRequisicao.criada(proprio);
                };

        var criar = new CriarRequisicaoCoordenadaUseCase(
                fonte, persistencia
        );
        var buscar = new ConsultarRequisicaoAutorizadaUseCase(
                fonte, consultas
        );
        var listar = new ListarRequisicoesAutorizadasUseCase(
                fonte, consultas
        );

        AdaptadorTipado tipado = new AdaptadorTipado(criar, buscar, listar);
        AdaptadorTextual texto = new AdaptadorTextual(criar, buscar, listar);

        var resultado = textual
                ? texto.criar(
                        pedidoId.toString(), recurso.getId().toString(), "2"
                )
                : tipado.criar(pedidoId, recurso.getId(), 2);

        conferir(
                resultado.requisicao() == proprio && criacoes[0] == 1,
                nome + ": criacao deriva autor e departamento na aplicacao"
        );

        Optional<Requisicao> encontrado = textual
                ? texto.buscar(pedidoId.toString())
                : tipado.buscar(pedidoId);

        conferir(
                encontrado.orElseThrow() == proprio
                        && usuario.equals(consultas.autorRecebido)
                        && departamento.getId().equals(
                                consultas.departamentoRecebido
                        ),
                nome + ": consulta utiliza escopo da identidade"
        );

        List<Requisicao> lista = textual
                ? texto.listar(departamento.getId().toString())
                : tipado.listar(departamento.getId());

        conferir(
                lista.size() == 1 && lista.get(0) == proprio,
                nome + ": listagem utiliza o caso de uso autorizado"
        );

        // Simula uma resposta indevida do armazenamento para verificar
        // a defesa da aplicação, independentemente do adaptador de entrada.
        consultas.resposta = pedido(
                pedidoId, departamento, recurso, UUID.randomUUID()
        );

        conferir(
                (textual
                        ? texto.buscar(pedidoId.toString())
                        : tipado.buscar(pedidoId)).isEmpty(),
                nome + ": pedido de colega nao e devolvido"
        );

        fonte.atualizar(new ContextoIdentidade(
                usuario,
                PerfilUsuario.ARQUITETO_CLOUD,
                true,
                departamento.getId()
        ));

        negar(
                () -> {
                    if (textual) {
                        texto.criar(
                                pedidoId.toString(),
                                recurso.getId().toString(),
                                "2"
                        );
                    } else {
                        tipado.criar(pedidoId, recurso.getId(), 2);
                    }
                },
                () -> criacoes[0] == 1,
                nome + ": arquiteto nao chega a persistencia de criacao"
        );

        fonte.atualizar(contexto(usuario, departamento.getId(), false));
        int consultasAntes = consultas.chamadas;

        negar(
                () -> {
                    if (textual) {
                        texto.buscar(pedidoId.toString());
                    } else {
                        tipado.buscar(pedidoId);
                    }
                },
                () -> consultas.chamadas == consultasAntes,
                nome + ": identidade inativa nao chega ao armazenamento"
        );
    }

    private static ContextoIdentidade contexto(
            UUID usuario,
            UUID departamento,
            boolean ativo
    ) {
        return new ContextoIdentidade(
                usuario, PerfilUsuario.DESENVOLVEDOR, ativo, departamento
        );
    }

    private static Requisicao pedido(
            UUID id,
            Departamento departamento,
            RecursoCatalogo recurso,
            UUID autor
    ) {
        Requisicao pedido = Requisicao.criarComId(id, departamento, autor);
        pedido.adicionarItem(new ItemRequisicao(recurso, 2));
        return pedido;
    }

    private record AdaptadorTipado(
            CriarRequisicaoCoordenadaUseCase criacao,
            ConsultarRequisicaoAutorizadaUseCase consulta,
            ListarRequisicoesAutorizadasUseCase listagem
    ) {
        ResultadoCriacaoRequisicao criar(
                UUID pedido,
                UUID recurso,
                int quantidade
        ) {
            return criacao.executar(
                    pedido,
                    List.of(new ItemSolicitado(recurso, quantidade))
            );
        }

        Optional<Requisicao> buscar(UUID pedido) {
            return consulta.executar(pedido);
        }

        List<Requisicao> listar(UUID departamento) {
            return listagem.executar(departamento);
        }
    }

    private record AdaptadorTextual(
            CriarRequisicaoCoordenadaUseCase criacao,
            ConsultarRequisicaoAutorizadaUseCase consulta,
            ListarRequisicoesAutorizadasUseCase listagem
    ) {
        ResultadoCriacaoRequisicao criar(
                String pedido,
                String recurso,
                String quantidade
        ) {
            return criacao.executar(
                    UUID.fromString(pedido),
                    List.of(new ItemSolicitado(
                            UUID.fromString(recurso),
                            Integer.valueOf(quantidade)
                    ))
            );
        }

        Optional<Requisicao> buscar(String pedido) {
            return consulta.executar(UUID.fromString(pedido));
        }

        List<Requisicao> listar(String departamento) {
            return listagem.executar(UUID.fromString(departamento));
        }
    }

    private static final class ConsultaControlada
            implements ConsultaRequisicaoPersistente {

        private Requisicao resposta;
        private UUID autorRecebido;
        private UUID departamentoRecebido;
        private int chamadas;

        @Override
        public Optional<Requisicao> buscarPorIdEAutorEDepartamento(
                UUID id,
                UUID autor,
                UUID departamento
        ) {
            chamadas++;
            autorRecebido = autor;
            departamentoRecebido = departamento;
            return Optional.of(resposta);
        }

        @Override
        public List<Requisicao> listarPorAutorEDepartamento(
                UUID autor,
                UUID departamento
        ) {
            chamadas++;
            autorRecebido = autor;
            departamentoRecebido = departamento;
            return List.of(resposta);
        }

        @Override
        public Optional<Requisicao> buscarPorId(UUID id) {
            throw new AssertionError(
                    "Este cenario nao deve consultar sem escopo."
            );
        }

        @Override
        public List<Requisicao> listarPorDepartamento(UUID departamento) {
            throw new AssertionError(
                    "Este cenario nao deve listar sem escopo de autor."
            );
        }
    }

    private static void negar(
            Runnable operacao,
            java.util.function.BooleanSupplier preservouChamadas,
            String descricao
    ) {
        try {
            operacao.run();
        } catch (AcessoNegadoException esperada) {
            conferir(preservouChamadas.getAsBoolean(), descricao);
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