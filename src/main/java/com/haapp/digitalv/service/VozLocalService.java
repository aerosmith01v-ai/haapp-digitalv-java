package com.haapp.digitalv.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Sistema de voz local — sin APIs externas, sin costo.
 * Estrategia en capas:
 *   1. ElevenLabs (si ELEVENLABS_API_KEY está configurado)
 *   2. espeak-ng  (instalado en Linux/Render)
 *   3. Web Speech API (fallback al navegador)
 */
@Service
public class VozLocalService {

    private static final Logger log = LoggerFactory.getLogger(VozLocalService.class);

    @Value("${dir.audios:./audios}")
    private String dirAudios;

    @Value("${server.port:3002}")
    private String port;

    private final boolean espeakDisponible;
    private final Map<String, Map<String, Object>> configUsuarios = new HashMap<>();

    public VozLocalService() {
        new File("./audios").mkdirs();
        this.espeakDisponible = verificarComando("espeak-ng", "--version");
        log.info("🎙  espeak-ng disponible: {}", espeakDisponible);
        log.info("🎙  ElevenLabs configurado: {}", elevenlabsConfigurado());
    }

    // ============================================================
    // GENERAR VOZ — usa la mejor opción disponible
    // ============================================================
    public Map<String, Object> generarVoz(String texto, String userId) {
        String audioId = "voz_" + System.currentTimeMillis();

        // Opción 1: ElevenLabs
        if (elevenlabsConfigurado()) {
            Map<String, Object> r = generarConElevenLabs(texto, audioId);
            if (r != null) return r;
        }

        // Opción 2: espeak-ng local
        if (espeakDisponible) {
            Map<String, Object> r = generarConEspeak(texto, audioId);
            if (r != null) return r;
        }

        // Opción 3: Web Speech API (frontend)
        return Map.of(
            "audioId",    audioId,
            "metodo",     "web_speech_api",
            "texto",      texto,
            "mensaje",    "Usar Web Speech API del navegador",
            "disponible", true
        );
    }

    private Map<String, Object> generarConEspeak(String texto, String audioId) {
        try {
            String wav = dirAudios + "/" + audioId + ".wav";
            Process proc = new ProcessBuilder(
                "espeak-ng", "-v", "es-la", "-s", "140", "-p", "60", "-a", "180", "-w", wav, texto
            ).redirectErrorStream(true).start();
            proc.waitFor();

            if (new File(wav).exists()) {
                String base = baseUrl();
                return Map.of(
                    "audioId",  audioId,
                    "url",      base + "/audios/" + audioId + ".wav",
                    "metodo",   "espeak-ng",
                    "texto",    texto,
                    "duracion", texto.length() * 0.07
                );
            }
        } catch (Exception e) {
            log.warn("espeak-ng falló: {}", e.getMessage());
        }
        return null;
    }

    private Map<String, Object> generarConElevenLabs(String texto, String audioId) {
        String apiKey  = System.getenv("ELEVENLABS_API_KEY");
        String voiceId = System.getenv("ELEVENLABS_VOICE_ID");
        if (apiKey == null || voiceId == null) return null;
        try {
            String body = "{\"text\":\"" + texto.replace("\"", "\\\"") + "\","
                + "\"model_id\":\"eleven_multilingual_v2\","
                + "\"voice_settings\":{\"stability\":0.5,\"similarity_boost\":0.85}}";

            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("https://api.elevenlabs.io/v1/text-to-speech/" + voiceId))
                .header("xi-api-key", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "audio/mpeg")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                .timeout(java.time.Duration.ofSeconds(20))
                .build();

            java.net.http.HttpResponse<byte[]> resp = client.send(
                req, java.net.http.HttpResponse.BodyHandlers.ofByteArray()
            );

            if (resp.statusCode() == 200) {
                String mp3 = dirAudios + "/" + audioId + ".mp3";
                Files.write(Paths.get(mp3), resp.body());
                return Map.of(
                    "audioId", audioId,
                    "url",     baseUrl() + "/audios/" + audioId + ".mp3",
                    "metodo",  "elevenlabs",
                    "texto",   texto
                );
            }
        } catch (Exception e) {
            log.warn("ElevenLabs falló: {}", e.getMessage());
        }
        return null;
    }

    // ============================================================
    // CONFIG POR USUARIO
    // ============================================================
    public Map<String, Object> configurarVoz(String userId, Map<String, Object> config) {
        configUsuarios.put(userId, config);
        return Map.of("ok", true, "userId", userId, "config", config);
    }

    public Map<String, Object> obtenerConfig(String userId) {
        return configUsuarios.getOrDefault(userId, Map.of(
            "velocidad", 1.0,
            "tono",      0,
            "idioma",    "es-MX",
            "metodo",    elevenlabsConfigurado() ? "elevenlabs"
                       : espeakDisponible        ? "espeak-ng"
                       : "web_speech_api"
        ));
    }

    public Map<String, Object> estado() {
        return Map.of(
            "espeak_disponible",     espeakDisponible,
            "elevenlabs_disponible", elevenlabsConfigurado(),
            "metodo_activo",         elevenlabsConfigurado() ? "ElevenLabs"
                                   : espeakDisponible        ? "espeak-ng"
                                   : "Web Speech API",
            "usuarios_configurados", configUsuarios.size()
        );
    }

    // ============================================================
    // HELPERS
    // ============================================================
    private boolean elevenlabsConfigurado() {
        String k = System.getenv("ELEVENLABS_API_KEY");
        return k != null && !k.isBlank();
    }

    private String baseUrl() {
        String env = System.getenv("MI_URL");
        return (env != null && !env.isBlank()) ? env : "http://localhost:" + port;
    }

    private boolean verificarComando(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).start();
            p.waitFor();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
