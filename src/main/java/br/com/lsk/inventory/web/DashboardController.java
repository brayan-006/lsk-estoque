package br.com.lsk.inventory.web;

import br.com.lsk.inventory.infrastructure.persistence.DashboardReadRepository;
import br.com.lsk.inventory.web.dto.DashboardViews.AlertView;
import br.com.lsk.inventory.web.dto.DashboardViews.MovementView;
import br.com.lsk.inventory.web.dto.DashboardViews.ProductView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardReadRepository reads;

    @GetMapping("/alerts")
    public List<AlertView> alerts() {
        return reads.openAlerts();
    }

    @GetMapping("/movements")
    public List<MovementView> movements(@RequestParam(defaultValue = "50") int limit) {
        return reads.recentMovements(Math.max(1, Math.min(limit, 200)));
    }

    @GetMapping("/products")
    public List<ProductView> products() {
        return reads.products();
    }
}
