package br.mackenzie.finops.infraestrutura.servidor;

import br.mackenzie.finops.aplicacao.CriarRequisicaoCoordenadaUseCase;
import br.mackenzie.finops.aplicacao.ListarCatalogoAutorizadoUseCase;
import br.mackenzie.finops.infraestrutura.persistencia.ConfiguracaoBanco;
import br.mackenzie.finops.infraestrutura.persistencia.ConsultaCatalogoJdbc;
import br.mackenzie.finops.infraestrutura.persistencia.CriacaoRequisicaoJdbc;
import br.mackenzie.finops.infraestrutura.persistencia.FabricaConexoes;
import br.mackenzie.finops.infraestrutura.persistencia.UsuariosJdbc;

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
    ListarCatalogoAutorizadoUseCase listarCatalogo(UsuariosJdbc usuarios, FabricaConexoes fabrica) {
        return new ListarCatalogoAutorizadoUseCase(
                () -> usuarioAutenticado().flatMap(usuarios::identidadeAtual), new ConsultaCatalogoJdbc(fabrica)
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
