package com.vault.reports;
import java.util.Map; import org.springframework.boot.SpringApplication; import org.springframework.boot.autoconfigure.SpringBootApplication; import org.springframework.web.bind.annotation.*; import reactor.core.publisher.Mono;
@SpringBootApplication public class ReportsApplication { public static void main(String[] args) { SpringApplication.run(ReportsApplication.class, args); } }
@RestController @RequestMapping("/reports") class ReportsController { @GetMapping("/summary") Mono<Map<String,Object>> summary(){return Mono.just(Map.of("totalTransfers",0,"totalVolume",0,"currency","EUR"));} @GetMapping("/transactions") Mono<Object> transactions(){return Mono.just(java.util.List.of());} }
