package com.haapp.digitalv.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SincronizacionService {

    private static final Logger log = LoggerFactory.getLogger(SincronizacionService.class);

    @Value("${sofi.node.url:}")
    private String sofiNodeUrl;

    @Value("${sofi.python.url:}")
    private String sofiPythonUrl;

    @Value("${mi.url:}")
    private String miUrl;

    @Value("${hz.kuhul:12.3}")
    private double hzKuhul;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Map<String, Object>> patronesAprendidos = new CopyOnWriteArrayList<>();
    private final Map<String, Object> estadoRed = new HashMap<>();
    private int totalSincronizaciones = 0;

    @Scheduled(fixedDelay = 840000)
    public void keepAlive() {
        if (miUrl == null || miUrl.isBlank()) return;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(miUrl + "/health"))
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            log.info("💓 [keep-alive] vivo ✅ ({})", resp.statusCode());
        } catch (Exception e) {
            log.warn("⚠️ [keep-alive] Sin respuesta: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 300000)
    public void sincronizarConHermanas() {
        log.info("🔄 Sincronización SOFI — ciclo {}", totalSincronizaciones + 1);
        sincronizarCon("Node.js", sofiNodeUrl);
        sincronizarCon("Python",  sofiPythonUrl);
        totalSincronizaciones++;
        log.info("✅ Ciclo {} completado. Patrones: {}", totalSincronizaciones, patronesAprendidos.size());
    }

    private void sincronizarCon(String nombre, String url) {
        if (url == null || url.isBlank()) {
            log.warn("⚠️ URL de SOFI {} no configurada", nombre);
            return;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/api/estado"))
                    .timeout(Duration.ofSeconds(8))
                    .GET().build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                log.info("✅ SOFI {} respondió", nombre);
                estadoRed.put("sofi_" + nombre.toLowerCase(), Map.of(
                    "status", "activa",
                    "ultima_sync", new Date().toInstant().toString()
                ));
                if (!patronesAprendidos.isEmpty()) enviarPatrones(nombre, url);
            }
        } catch (Exception e) {
            log.warn("❌ SOFI {} no disponible: {}", nombre, e.getMessage());
            estadoRed.put("sofi_" + nombre.toLowerCase(), Map.of(
                "status", "no_disponible",
                "error", e.getMessage()
            ));
        }
    }

    private void enviarPatrones(String nombre, String url) {
        try {
            List<Map<String, Object>> ultimos = patronesAprendidos.size() > 50
                ? patronesAprendidos.subList(patronesAprendidos.size() - 50, patronesAprendidos.size())
                : new ArrayList<>(patronesAprendidos);
            String body = mapper.writeValueAsString(Map.of("patrones", ultimos, "fuente", "haapp-java"));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/api/aprender_masivo"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            log.info("📤 {} patrones → SOFI {} ({})", ultimos.size(), nombre, resp.statusCode());
        } catch (Exception e) {
            log.warn("❌ Error enviando patrones a {}: {}", nombre, e.getMessage());
        }
    }

    public Map<String, Object> aprenderMasivo(List<Map<String, Object>> patrones, String fuente) {
        int aprendidos = 0;
        for (Map<String, Object> p : patrones) {
            if (p.get("tema") != null && p.get("datos") != null) {
                Map<String, Object> nuevo = new HashMap<>(p);
                nuevo.put("fuente_original", fuente);
                nuevo.put("fecha_recepcion", new Date().toInstant().toString());
                patronesAprendidos.add(nuevo);
                aprendidos++;
            }
        }
        if (patronesAprendidos.size() > 500)
            patronesAprendidos.subList(0, patronesAprendidos.size() - 500).clear();
        log.info("🧠 Aprendidos {} patrones de {}", aprendidos, fuente);
        return Map.of("ok", true, "aprendidos", aprendidos, "total", patronesAprendidos.size());
    }

    public void agregarPatron(String tema, Object datos, String contexto) {
        Map<String, Object> p = new HashMap<>();
        p.put("tema", tema); p.put("datos", datos);
        p.put("contexto", contexto); p.put("fuente", "haapp-java");
        p.put("fecha", new Date().toInstant().toString());
        patronesAprendidos.add(p);
        if (patronesAprendidos.size() > 500)
            patronesAprendidos.subList(0, 100).clear();
    }

    public Map<String, Object> getEstadoRed() {
        Map<String, Object> estado = new HashMap<>(estadoRed);
        estado.put("java_status", "activa");
        estado.put("patrones_java", patronesAprendidos.size());
        estado.put("sincronizaciones", totalSincronizaciones);
        estado.put("hz", hzKuhul);
        estado.put("timestamp", new Date().toInstant().toString());
        estado.put("node_configurado", sofiNodeUrl != null && !sofiNodeUrl.isBlank());
        estado.put("python_configurado", sofiPythonUrl != null && !sofiPythonUrl.isBlank());
        estado.put("keepalive_activo", miUrl != null && !miUrl.isBlank());
        return estado;
    }

    public List<Map<String, Object>> getPatrones() { return patronesAprendidos; }
}
