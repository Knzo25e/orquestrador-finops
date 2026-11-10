package br.mackenzie.finops.infraestrutura.servidor;

import br.mackenzie.finops.infraestrutura.http.CatalogoController;
import br.mackenzie.finops.infraestrutura.http.IntervencoesController;
import br.mackenzie.finops.infraestrutura.http.OperacoesRequisicaoController;
import br.mackenzie.finops.infraestrutura.http.RequisicaoController;
import br.mackenzie.finops.infraestrutura.http.TratamentoErrosHttp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;

@SpringBootApplication
@Import({
        CatalogoController.class, RequisicaoController.class,
        OperacoesRequisicaoController.class, IntervencoesController.class, TratamentoErrosHttp.class
})
@PropertySource("classpath:finops-servidor.properties")
public class FinopsServidor {
    public static void main(String[] args) {
        if (args.length > 0 && "cadastrar-demonstracao".equals(args[0])) {
            ContasDemonstracao.executar(args);
            return;
        }
        SpringApplication.run(FinopsServidor.class, args);
    }
}
