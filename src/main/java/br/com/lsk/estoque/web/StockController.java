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

    @GetMapping("/estoque/{sku}")
    public EstoqueDetalhe consultar(@PathVariable String sku) {
        return consulta.consultar(sku);
    }

    @PostMapping("/estoque/entradas/nfe")
    public ResponseEntity<ResultadoEntrada> entradaNfe(@Valid @RequestBody EntradaNfeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(entrada.registrarNfe(request.toCommand()));
    }

    @GetMapping("/produtos")
    public List<ProdutoAbcView> listarPorClasse(@RequestParam("classe") String classe) {
        return consulta.listarPorClasse(parseClasse(classe));
    }

    @PostMapping("/estoque/curva-abc/recalcular")
    public ResultadoCurvaAbc recalcularCurvaAbc() {
        return curvaAbc.recalcular();
    }

    private ClasseAbc parseClasse(String valor) {
        try {
            return ClasseAbc.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new RegraDeNegocioException("Invalid ABC class: '" + valor + "'. Use A, B or C.");
        }
    }
}
