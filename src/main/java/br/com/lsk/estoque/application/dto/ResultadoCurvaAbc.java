package br.com.lsk.estoque.application.dto;

import java.time.OffsetDateTime;

public record ResultadoCurvaAbc(int produtosReclassificados, int produtosRebaixadosParaC, int janelaDias,
                                OffsetDateTime calculadoEm) {
}
