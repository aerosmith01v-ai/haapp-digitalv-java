package com.haapp.digitalv.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sincronización bidireccional entre las 3 instancias SOFI:
 *   Node.js v6.0  ←→  Python v1.1  ←→  Java v1.0 (esta)
 *
 * Las 3 funcionan como una sola mente distribuida.
 * Si una cae, las otras dos siguen operando.
 */
@Service
public class SincronizacionService {

    private static final Logger log = LoggerFactory.getLogger(SincronizacionService.class);

    @Value("${sofi.node.url:}")
    private String sofiNodeUrl;

    @Value("${sofi.python.url:}")
    private String sofiPythonUrl;

    @Value("${hz.kuhul:12.3}")
    private double hzKuhul;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    // Base de conocimiento compartida
    private final List<Map<String, Object>> patronesAprendidos = new CopyOnWriteArrayList<>();
    private final Map<String, Object> estadoRed = new HashMap<>();
    private int totalSincronizaciones = 0;

    // ── Sincronización automática cada 5 minutos ──
    @Scheduled(fixedDelay = 300000)
    public void sincronizarConHermanas() {
        log.info("🔄 Iniciando sincronización bidireccional SOFI...");
        sincronizarCon("Node.js", sofiNodeUrl);
        sincronizarCon("Python",  sofiPythonUrl);
        totalSincronizaciones++;
        log.info("✅ Ciclo {} completado. Patrones: {}", totalSincronizaciones, patronesAprendidos.size());
    }

    private void sincronizarCon(String nombre, String url) {
        if (url == null || url.isBlank()) {
            log.warn("⚠️  URL de SOFI {} no configurada", nombre);
            return;
        }
        try {
            // 1. Obtener estado de la hermana
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/api/estado"))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                log.info("✅ SOFI {} respondió — sincronizando patrones", nombre);
                estadoRed.put("sofi_" + nombre.toLowerCase(), Map.of(
                    "status", "activa",
                    "ultima_sync", new Date().toInstant().toString()
                ));

                // 2. Enviar nuestros patrones a la hermana
                if (!patronesAprendidos.isEmpty()) {
                    enviarPatrones(nombre, url);
                }
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
                : patronesAprendidos;

            String body = "{\"patrones\":" + listaAJson(ultimos) + ",\"fuente\":\"haapp-java\"}";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/api/aprender_masivo"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            log.info("📤 Enviados {} patrones a SOFI {} — status {}", ultimos.size(), nombre, resp.statusCode());
        } catch (Exception e) {
            log.warn("❌ Error enviando patrones a {}: {}", nombre, e.getMessage());
        }
    }

    // ── Aprender desde cualquier hermana ──
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
        // Limitar memoria
        if (patronesAprendidos.size() > 500) {
            patronesAprendidos.subList(0, patronesAprendidos.size() - 500).clear();
        }
        log.info("🧠 Aprendidos {} patrones de {}", aprendidos, fuente);
        return Map.of("ok", true, "aprendidos", aprendidos, "total", patronesAprendidos.size());
    }

    // ── Agregar patrón propio ──
    public void agregarPatron(String tema, Object datos, String contexto) {
        Map<String, Object> p = new HashMap<>();
        p.put("tema", tema);
        p.put("datos", datos);
        p.put("contexto", contexto);
        p.put("fuente", "haapp-java");
        p.put("fecha", new Date().toInstant().toString());
        patronesAprendidos.add(p);
        if (patronesAprendidos.size() > 500)
            patronesAprendidos.subList(0, 100).clear();
    }

    // ── Estado de la red ──
    public Map<String, Object> getEstadoRed() {
        Map<String, Object> estado = new HashMap<>(estadoRed);
        estado.put("java_status",        "activa");
        estado.put("patrones_java",       patronesAprendidos.size());
        estado.put("sincronizaciones",    totalSincronizaciones);
        estado.put("hz",                  hzKuhul);
        estado.put("timestamp",           new Date().toInstant().toString());
        estado.put("node_configurado",    !sofiNodeUrl.isBlank());
        estado.put("python_configurado",  !sofiPythonUrl.isBlank());
        return estado;
    }

    public List<Map<String, Object>> getPatrones() { return patronesAprendidos; }

    // ── Utilidad simple JSON ──
    private String listaAJson(List<Map<String, Object>> lista) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < lista.size(); i++) {
            sb.append("{");
            lista.get(i).forEach((k, v) -> sb.append("\"").append(k).append("\":\"").append(v).append("\","));
            if (sb.charAt(sb.length()-1) == ',') sb.deleteCharAt(sb.length()-1);
            sb.append(i < lista.size()-1 ? "}," : "}");
        }
        sb.append("]");
        return sb.toString();
    }
}
