package br.mackenzie.finops.aplicacao;

import br.mackenzie.finops.aplicacao.portas.ConsultaRequisicaoPersistente;
import br.mackenzie.finops.aplicacao.portas.ProvedorIdentidade;
import br.mackenzie.finops.aplicacao.seguranca.ContextoIdentidade;
import br.mackenzie.finops.aplicacao.seguranca.PerfilUsuario;
import br.mackenzie.finops.dominio.entidades.Departamento;
import br.mackenzie.finops.dominio.entidades.Requisicao;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class VerificarEscopoConsultasAutorizadas {

    private static int verificacoes;

    public static void main(String[] args) {
        UUID autorId = UUID.randomUUID();
        UUID colegaId = UUID.randomUUID();
        UUID pedidoId = UUID.randomUUID();

        Departamento departamento = departamento();
        Departamento outroDepartamento = departamento();

        Requisicao proprio = Requisicao.criarComId(
                pedidoId, departamento, autorId
        );
        Requisicao colega = Requisicao.criarComId(
                pedidoId, departamento, colegaId
        );
        Requisicao outroVinculo = Requisicao.criarComId(
                pedidoId, outroDepartamento, autorId
        );
        Requisicao antigo = Requisicao.criarComId(
                pedidoId, departamento
        );

        ProvedorIdentidade desenvolvedor = () -> Optional.of(
                new ContextoIdentidade(
                        autorId,
                        PerfilUsuario.DESENVOLVEDOR,
                        true,
                        departamento.getId()
                )
        );

        ConsultaControlada consultas = new ConsultaControlada();

        ConsultarRequisicaoAutorizadaUseCase buscar =
                new ConsultarRequisicaoAutorizadaUseCase(
                        desenvolvedor, consultas
                );

        ListarRequisicoesAutorizadasUseCase listar =
                new ListarRequisicoesAutorizadasUseCase(
                        desenvolvedor, consultas
                );

        consultas.pedido = Optional.of(proprio);

        conferir(
                buscar.executar(pedidoId).orElseThrow() == proprio
                        && consultas.chamadaCorreta(
                                "buscarFiltrado",
                                pedidoId,
                                autorId,
                                departamento.getId()
                        ),
                "Desenvolvedor busca com autor e departamento da identidade"
        );

        consultas.pedido = Optional.empty();

        conferir(
                buscar.executar(pedidoId).isEmpty(),
                "Ausencia no armazenamento retorna Optional.empty"
        );

        // Simula um adaptador que violou o filtro obrigatório.
        consultas.pedido = Optional.of(colega);

        conferir(
                buscar.executar(pedidoId).isEmpty(),
                "Defesa adicional nao devolve pedido de colega"
        );

        consultas.pedido = Optional.of(outroVinculo);

        conferir(
                buscar.executar(pedidoId).isEmpty(),
                "Defesa adicional nao devolve pedido de outro departamento"
        );

        consultas.pedido = Optional.of(antigo);

        conferir(
                buscar.executar(pedidoId).isEmpty(),
                "Defesa adicional nao devolve pedido sem autor ao desenvolvedor"
        );

        consultas.pedido = Optional.of(Requisicao.criarComId(
                UUID.randomUUID(), departamento, autorId
        ));

        rejeitar(
                IllegalStateException.class,
                () -> buscar.executar(pedidoId),
                "Busca rejeita resultado com UUID diferente"
        );

        consultas.lista = new ArrayList<>(List.of(proprio));

        List<Requisicao> resultado = listar.executar(departamento.getId());

        conferir(
                resultado.size() == 1
                        && resultado.get(0) == proprio
                        && consultas.chamadaCorreta(
                                "listarFiltrado",
                                null,
                                autorId,
                                departamento.getId()
                        ),
                "Desenvolvedor lista com autor e departamento da identidade"
        );

        rejeitar(
                UnsupportedOperationException.class,
                () -> resultado.add(colega),
                "Lista devolvida impede inclusao"
        );

        consultas.lista.clear();

        conferir(
                resultado.size() == 1,
                "Alterar lista do adaptador nao altera lista devolvida"
        );

        consultas.lista = List.of(proprio, colega);

        rejeitar(
                IllegalStateException.class,
                () -> listar.executar(departamento.getId()),
                "Listagem rejeita resultado contendo pedido de colega"
        );

        consultas.lista = List.of(outroVinculo);

        rejeitar(
                IllegalStateException.class,
                () -> listar.executar(departamento.getId()),
                "Listagem rejeita resultado de outro departamento"
        );

        consultas.lista = List.of(antigo);

        rejeitar(
                IllegalStateException.class,
                () -> listar.executar(departamento.getId()),
                "Listagem do desenvolvedor rejeita pedido sem autor"
        );

        ProvedorIdentidade arquiteto = () -> Optional.of(
                new ContextoIdentidade(
                        UUID.randomUUID(),
                        PerfilUsuario.ARQUITETO_CLOUD,
                        true,
                        null
                )
        );

        ConsultarRequisicaoAutorizadaUseCase buscarArquiteto =
                new ConsultarRequisicaoAutorizadaUseCase(
                        arquiteto, consultas
                );

        ListarRequisicoesAutorizadasUseCase listarArquiteto =
                new ListarRequisicoesAutorizadasUseCase(
                        arquiteto, consultas
                );

        consultas.pedido = Optional.of(antigo);

        conferir(
                buscarArquiteto.executar(pedidoId).orElseThrow() == antigo
                        && consultas.chamadaCorreta(
                                "buscar",
                                pedidoId,
                                null,
                                null
                        ),
                "Arquiteto sem vinculo consulta pedido sem autoria"
        );

        consultas.pedido = Optional.of(outroVinculo);

        conferir(
                buscarArquiteto.executar(pedidoId).orElseThrow() == outroVinculo,
                "Arquiteto consulta pedido de outro departamento"
        );

        consultas.lista = List.of(proprio, antigo);

        conferir(
                listarArquiteto.executar(departamento.getId()).size() == 2
                        && consultas.chamadaCorreta(
                                "listar",
                                null,
                                null,
                                departamento.getId()
                        ),
                "Arquiteto lista departamento incluindo autoria desconhecida"
        );

        consultas.lista = List.of(outroVinculo);

        conferir(
                listarArquiteto.executar(outroDepartamento.getId())
                        .get(0) == outroVinculo
                        && consultas.chamadaCorreta(
                                "listar",
                                null,
                                null,
                                outroDepartamento.getId()
                        ),
                "Arquiteto pode selecionar outro departamento"
        );

        consultas.pedido = null;

        rejeitar(
                IllegalStateException.class,
                () -> buscarArquiteto.executar(pedidoId),
                "Busca rejeita Optional nulo do adaptador"
        );

        consultas.lista = null;

        rejeitar(
                IllegalStateException.class,
                () -> listarArquiteto.executar(departamento.getId()),
                "Listagem rejeita lista nula do adaptador"
        );

        System.out.println(
                "Todas as " + verificacoes + " verificacoes passaram."
        );
    }

    private static Departamento departamento() {
        return Departamento.reconstituir(
                UUID.randomUUID(),
                "Departamento do teste",
                new BigDecimal("1000.00"),
                new BigDecimal("800.00")
        );
    }

    private static void conferir(boolean condicao, String descricao) {
        if (!condicao) {
            throw new IllegalStateException("FALHOU: " + descricao);
        }

        verificacoes++;
        System.out.println("PASSOU: " + descricao);
    }

    private static void rejeitar(
            Class<? extends RuntimeException> tipo,
            Runnable operacao,
            String descricao
    ) {
        try {
            operacao.run();
        } catch (RuntimeException falha) {
            if (!tipo.isInstance(falha)) {
                throw new IllegalStateException(
                        "FALHOU: excecao inesperada em " + descricao,
                        falha
                );
            }

            conferir(true, descricao);
            return;
        }

        throw new IllegalStateException("FALHOU: " + descricao);
    }

    private static final class ConsultaControlada
            implements ConsultaRequisicaoPersistente {

        private Optional<Requisicao> pedido = Optional.empty();
        private List<Requisicao> lista = List.of();

        private String metodo;
        private UUID requisicaoRecebida;
        private UUID autorRecebido;
        private UUID departamentoRecebido;

        @Override
        public Optional<Requisicao> buscarPorIdEAutorEDepartamento(
                UUID id,
                UUID autorId,
                UUID departamentoId
        ) {
            registrar("buscarFiltrado", id, autorId, departamentoId);
            return pedido;
        }

        @Override
        public List<Requisicao> listarPorAutorEDepartamento(
                UUID autorId,
                UUID departamentoId
        ) {
            registrar("listarFiltrado", null, autorId, departamentoId);
            return lista;
        }

        @Override
        public Optional<Requisicao> buscarPorId(UUID id) {
            registrar("buscar", id, null, null);
            return pedido;
        }

        @Override
        public List<Requisicao> listarPorDepartamento(UUID departamentoId) {
            registrar("listar", null, null, departamentoId);
            return lista;
        }

        private void registrar(
                String metodo,
                UUID requisicaoId,
                UUID autorId,
                UUID departamentoId
        ) {
            this.metodo = metodo;
            this.requisicaoRecebida = requisicaoId;
            this.autorRecebido = autorId;
            this.departamentoRecebido = departamentoId;
        }

        private boolean chamadaCorreta(
                String metodoEsperado,
                UUID requisicaoEsperada,
                UUID autorEsperado,
                UUID departamentoEsperado
        ) {
            return metodoEsperado.equals(metodo)
                    && java.util.Objects.equals(
                            requisicaoEsperada, requisicaoRecebida
                    )
                    && java.util.Objects.equals(
                            autorEsperado, autorRecebido
                    )
                    && java.util.Objects.equals(
                            departamentoEsperado, departamentoRecebido
                    );
        }
    }
}