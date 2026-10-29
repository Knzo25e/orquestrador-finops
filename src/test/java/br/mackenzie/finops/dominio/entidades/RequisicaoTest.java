package br.mackenzie.finops.dominio.entidades;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RequisicaoTest {

    @Test
    public void testAprovacaoTecnicaComSucesso() {
        Requisicao req = new Requisicao();
        req.setStatus("REVISAO_PENDENTE");
        
        req.aprovarTecnicamente();
        
        assertEquals("AGUARDANDO_AJUSTE_ORCAMENTARIO", req.getStatus(), 
            "A requisição deve mudar para aguardando ajuste orçamentário após aprovação técnica.");
    }

    @Test
    public void testBloqueioDeAprovacaoTecnicaIndevida() {
        Requisicao req = new Requisicao();
        req.setStatus("RASCUNHO"); 
        
        assertThrows(IllegalStateException.class, () -> {
            req.aprovarTecnicamente();
        }, "Deve lançar exceção se tentar aprovar tecnicamente uma requisição que não está em revisão.");
    }
}
