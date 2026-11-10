package br.mackenzie.finops.infraestrutura.servidor;

import br.mackenzie.finops.aplicacao.ConsultarRequisicaoAutorizadaUseCase;
import br.mackenzie.finops.aplicacao.CriarRequisicaoCoordenadaUseCase;
import br.mackenzie.finops.aplicacao.ListarCatalogoAutorizadoUseCase;
import br.mackenzie.finops.aplicacao.ListarRequisicoesAutorizadasUseCase;
import br.mackenzie.finops.aplicacao.ValidarRequisicaoAutorizadaUseCase;
import br.mackenzie.finops.aplicacao.portas.ProvedorIdentidade;
import br.mackenzie.finops.infraestrutura.persistencia.ConfiguracaoBanco;
import br.mackenzie.finops.infraestrutura.persistencia.ConsultaCatalogoJdbc;
import br.mackenzie.finops.infraestrutura.persistencia.ConsultaRequisicaoJdbc;
import br.mackenzie.finops.infraestrutura.persistencia.CriacaoRequisicaoJdbc;
import br.mackenzie.finops.infraestrutura.persistencia.FabricaConexoes;
import br.mackenzie.finops.infraestrutura.persistencia.UsuariosJdbc;
import br.mackenzie.finops.infraestrutura.persistencia.ValidacaoRequisicaoJdbc;

import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

@Configuration(proxyBeanMethods = false)
class ComposicaoFinops {
    @Bean
    FabricaConexoes fabricaConexoes() {
        return new FabricaConexoes(ConfiguracaoBanco.carregarDoAmbiente());
    }

    @Bean
    UsuariosJdbc usuarios(FabricaConexoes fabrica) {
        return new UsuariosJdbc(fabrica);
    }

    @Bean
    ProvedorIdentidade identidadeAtual(UsuariosJdbc usuarios) {
        return () -> usuarioAutenticado().flatMap(usuarios::identidadeAtual);
    }

    @Bean
    ListarCatalogoAutorizadoUseCase listarCatalogo(ProvedorIdentidade identidade, FabricaConexoes fabrica) {
        return new ListarCatalogoAutorizadoUseCase(identidade, new ConsultaCatalogoJdbc(fabrica));
    }

    @Bean
    ConsultaRequisicaoJdbc consultasRequisicoes(FabricaConexoes fabrica) {
        return new ConsultaRequisicaoJdbc(fabrica);
    }

    @Bean
    ConsultarRequisicaoAutorizadaUseCase consultarRequisicao(
            ProvedorIdentidade identidade, ConsultaRequisicaoJdbc consultas
    ) {
        return new ConsultarRequisicaoAutorizadaUseCase(identidade, consultas);
    }

    @Bean
    ListarRequisicoesAutorizadasUseCase listarRequisicoes(
            ProvedorIdentidade identidade, ConsultaRequisicaoJdbc consultas
    ) {
        return new ListarRequisicoesAutorizadasUseCase(identidade, consultas);
    }

    @Bean
    ValidarRequisicaoAutorizadaUseCase validarRequisicao(FabricaConexoes fabrica) {
        return new ValidarRequisicaoAutorizadaUseCase(
                ComposicaoFinops::usuarioAutenticado, new ValidacaoRequisicaoJdbc(fabrica)
        );
    }

    @Bean
    CriarRequisicaoCoordenadaUseCase criarRequisicao(FabricaConexoes fabrica) {
        return new CriarRequisicaoCoordenadaUseCase(
                ComposicaoFinops::usuarioAutenticado, new CriacaoRequisicaoJdbc(fabrica)
        );
    }

    private static Optional<UUID> usuarioAutenticado() {
        var autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao != null && autenticacao.isAuthenticated()
                && autenticacao.getPrincipal() instanceof UserDetails usuario) {
            return Optional.of(UUID.fromString(usuario.getUsername()));
        }
        return Optional.empty();
    }
}
