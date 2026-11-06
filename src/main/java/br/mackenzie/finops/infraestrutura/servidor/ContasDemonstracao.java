package br.mackenzie.finops.infraestrutura.servidor;

import br.mackenzie.finops.aplicacao.seguranca.PerfilUsuario;
import br.mackenzie.finops.infraestrutura.persistencia.ConfiguracaoBanco;
import br.mackenzie.finops.infraestrutura.persistencia.FabricaConexoes;
import br.mackenzie.finops.infraestrutura.persistencia.UsuariosJdbc;

import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.UUID;

import org.springframework.security.crypto.factory.PasswordEncoderFactories;

final class ContasDemonstracao {
    private ContasDemonstracao() {}

    static void executar(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Use cadastrar-demonstracao <UUID do departamento>.");
        }
        UUID departamento = UUID.fromString(args[1]);
        String desenvolvedor = lerHash("desenvolvedor");
        String arquiteto = lerHash("arquiteto");

        var fabrica = new FabricaConexoes(ConfiguracaoBanco.carregarDoAmbiente());
        try (var conexao = fabrica.abrir()) {
            conexao.setAutoCommit(false);
            try {
                UsuariosJdbc.cadastrar(conexao, "desenvolvedor", desenvolvedor,
                        PerfilUsuario.DESENVOLVEDOR, departamento);
                UsuariosJdbc.cadastrar(conexao, "arquiteto", arquiteto,
                        PerfilUsuario.ARQUITETO_CLOUD, null);
            } catch (SQLException falha) {
                conexao.rollback();
                throw falha;
            }
            conexao.commit();
        } catch (SQLException falha) {
            throw new IllegalStateException(
                    "Cadastro não confirmado. Confira o departamento, os usuários existentes e o MySQL."
            );
        }
        System.out.println("Contas desenvolvedor e arquiteto cadastradas.");
    }

    private static String lerHash(String login) {
        var console = System.console();
        if (console == null || !console.isTerminal()) {
            throw new IllegalStateException("Execute em terminal interativo para digitar a senha sem eco.");
        }
        char[] senha = console.readPassword("Senha para %s: ", login);
        if (senha == null) {
            throw new IllegalArgumentException("Senha obrigatória.");
        }
        var bytes = StandardCharsets.UTF_8.encode(CharBuffer.wrap(senha));
        try {
            if (senha.length < 12 || bytes.remaining() > 72) {
                throw new IllegalArgumentException("Use ao menos 12 caracteres e no máximo 72 bytes UTF-8.");
            }
            return PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(CharBuffer.wrap(senha));
        } finally {
            Arrays.fill(senha, '\0');
            Arrays.fill(bytes.array(), (byte) 0);
        }
    }
}
