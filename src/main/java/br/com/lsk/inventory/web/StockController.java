package br.com.lsk.inventory.web;

import br.com.lsk.inventory.application.dto.StockDetail;
import br.com.lsk.inventory.application.dto.ProductAbcView;
import br.com.lsk.inventory.application.dto.AbcAnalysisResult;
import br.com.lsk.inventory.application.dto.ReceiptResult;
import br.com.lsk.inventory.application.service.AbcAnalysisService;
import br.com.lsk.inventory.application.service.StockReceiptService;
import br.com.lsk.inventory.application.service.StockQueryService;
import br.com.lsk.inventory.domain.exception.BusinessRuleException;
import br.com.lsk.inventory.domain.model.AbcClass;
import br.com.lsk.inventory.web.dto.NfeReceiptRequest;
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

    private final StockQueryService query;
    private final StockReceiptService receipt;
    private final AbcAnalysisService abcAnalysis;

    @GetMapping("/stock/{sku}")
    public StockDetail query(@PathVariable String sku) {
        return query.query(sku);
    }

    @PostMapping("/stock/receipts/nfe")
    public ResponseEntity<ReceiptResult> nfeReceipt(@Valid @RequestBody NfeReceiptRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(receipt.registerNfe(request.toCommand()));
    }

    @GetMapping("/abc-analysis/products")
    public List<ProductAbcView> listByClass(@RequestParam("abcClass") String abcClass) {
        return query.listByClass(parseClass(abcClass));
    }

    @PostMapping("/stock/abc-analysis/recalculate")
    public AbcAnalysisResult recalculateAbcAnalysis() {
        return abcAnalysis.recalculate();
    }

    private AbcClass parseClass(String value) {
        try {
            return AbcClass.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Invalid ABC class: '" + value + "'. Use A, B or C.");
        }
    }
}
