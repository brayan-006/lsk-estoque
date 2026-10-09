package br.com.lsk.estoque;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EstoqueApplication {

    public static void main(String[] args) {
        SpringApplication.run(EstoqueApplication.class, args);
    }
}
