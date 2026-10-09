package br.com.lsk.estoque.web;

import br.com.lsk.estoque.application.dto.EstoqueDetalhe;
import br.com.lsk.estoque.application.dto.ProdutoAbcView;
import br.com.lsk.estoque.application.dto.ResultadoCurvaAbc;
import br.com.lsk.estoque.application.dto.ResultadoEntrada;
import br.com.lsk.estoque.application.service.CurvaAbcService;
import br.com.lsk.estoque.application.service.EntradaEstoqueService;
import br.com.lsk.estoque.application.service.EstoqueConsultaService;
import br.com.lsk.estoque.domain.exception.RegraDeNegocioException;
import br.com.lsk.estoque.domain.model.ClasseAbc;
import br.com.lsk.estoque.web.dto.EntradaNfeRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StockController {

    private final EstoqueConsultaService consulta;
    private final EntradaEstoqueService entrada;
    private final CurvaAbcService curvaAbc;

    /** Posição em tempo real: saldo vendável + lotes na ordem em que serão consumidos (FEFO/PEPS). */
    @GetMapping("/estoque/{sku}")
    public EstoqueDetalhe consultar(@PathVariable String sku) {
        return consulta.consultar(sku);
    }

    /** Entrada de NF-e (idempotente pela chave de acesso: reenvio devolve 409). */
    @PostMapping("/estoque/entradas/nfe")
    public ResponseEntity<ResultadoEntrada> entradaNfe(@Valid @RequestBody EntradaNfeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(entrada.registrarNfe(request.toCommand()));
    }

    /** Produtos de uma classe da Curva ABC: GET /api/v1/produtos?classe=A */
    @GetMapping("/produtos")
    public List<ProdutoAbcView> listarPorClasse(@RequestParam("classe") String classe) {
        return consulta.listarPorClasse(parseClasse(classe));
    }

    /** Recalcula a Curva ABC sob demanda (o job noturno faz o mesmo). */
    @PostMapping("/estoque/curva-abc/recalcular")
    public ResultadoCurvaAbc recalcularCurvaAbc() {
        return curvaAbc.recalcular();
    }

    private ClasseAbc parseClasse(String valor) {
        try {
            return ClasseAbc.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new RegraDeNegocioException("Classe ABC inválida: '" + valor + "'. Use A, B ou C.");
        }
    }
}
