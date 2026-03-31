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
 *
 * Estrategia en capas:
 *   1. espeak-ng (instalado en Linux Render) — voz robótica pero funcional
 *   2. Festival TTS (si está disponible)
 *   3. Simulado (genera archivo de texto con el contenido)
 *
 * Para la voz clonada real: usar ElevenLabs cuando haya presupuesto,
 * configurando ELEVENLABS_API_KEY + ELEVENLABS_VOICE_ID en Render.
 */
@Service
public class VozLocalService {

    private static final Logger log = LoggerFactory.getLogger(VozLocalService.class);

    @Value("${dir.audios:./audios}")
    private String dirAudios;

    @Value("${server.port:3002}")
    private String port;

    private boolean espeakDisponible = false;
    private boolean elevenlabsDisponible = false;

    // Config voz por usuario
    private final Map<String, Map<String, Object>> configUsuarios = new HashMap<>();

    public VozLocalService() {
        // Crear directorio de audios
        new File("./audios").mkdirs();
        // Verificar espeak
        espeakDisponible = verificarComando("espeak-ng --version");
        log.info("🎙  espeak-ng disponible: {}", espeakDisponible);
        // Verificar ElevenLabs env vars
        String elKey = System.getenv("ELEVENLABS_API_KEY");
        elevenlabsDisponible = elKey != null && !elKey.isBlank();
        log.info("🎙  ElevenLabs configurado: {}", elevenlabsDisponible);
    }

    // ── Generar voz — usa la mejor opción disponible ──
    public Map<String, Object> generarVoz(String texto, String userId) {
        String audioId = "voz_" + System.currentTimeMillis();
        String audioPath = dirAudios + "/" + audioId;

        // Opción 1: ElevenLabs (si está configurado)
        if (elevenlabsDisponible) {
            Map<String, Object> r = generarConElevenLabs(texto, audioId);
            if (r != null) return r;
        }

        // Opción 2: espeak-ng local
        if (espeakDisponible) {
            Map<String, Object> r = generarConEspeak(texto, audioId, audioPath);
            if (r != null) return r;
        }

        // Opción 3: Simulado — devuelve el texto para que el frontend use Web Speech API
        return Map.of(
            "audioId",   audioId,
            "metodo",    "web_speech_api",
            "texto",     texto,
            "mensaje",   "Usar Web Speech API del navegador",
            "disponible", true
        );
    }

    private Map<String, Object> generarConEspeak(String texto, String audioId, String audioPath) {
        try {
            String archivoWav = audioPath + ".wav";
            ProcessBuilder pb = new ProcessBuilder(
                "espeak-ng",
                "-v", "es-la",       // español latinoamericano
                "-s", "140",         // velocidad
                "-p", "60",          // tono
                "-a", "180",         // amplitud
                "-w", archivoWav,    // output wav
                texto
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            int exitCode = proc.waitFor();

            if (exitCode == 0 && new File(archivoWav).exists()) {
                String baseUrl = System.getenv("MI_URL") != null
                    ? System.getenv("MI_URL")
                    : "http://localhost:" + port;
                return Map.of(
                    "audioId",  audioId,
                    "url",      baseUrl + "/audios/" + audioId + ".wav",
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
            String body = "{\"text\":\"" + texto.replace("\"","\\\"") + "\","
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

            java.net.http.HttpResponse<byte[]> resp = client.send(req,
                java.net.http.HttpResponse.BodyHandlers.ofByteArray());

            if (resp.statusCode() == 200) {
                String mp3Path = dirAudios + "/" + audioId + ".mp3";
                Files.write(Paths.get(mp3Path), resp.body());
                String baseUrl = System.getenv("MI_URL") != null
                    ? System.getenv("MI_URL") : "http://localhost:" + port;
                return Map.of(
                    "audioId", audioId,
                    "url",     baseUrl + "/audios/" + audioId + ".mp3",
                    "metodo",  "elevenlabs",
                    "texto",   texto
                );
            }
        } catch (Exception e) {
            log.warn("ElevenLabs falló: {}", e.getMessage());
        }
        return null;
    }

    // ── Config por usuario ──
    public Map<String, Object> configurarVoz(String userId, Map<String, Object> config) {
        configUsuarios.put(userId, config);
        return Map.of("ok", true, "userId", userId, "config", config);
    }

    public Map<String, Object> obtenerConfig(String userId) {
        return configUsuarios.getOrDefault(userId, Map.of(
            "velocidad", 1.0, "tono", 0, "idioma", "es-MX",
            "metodo", elevenlabsDisponible ? "elevenlabs" : espeakDisponible ? "espeak" : "web_speech"
        ));
    }

    public Map<String, Object> estado() {
        return Map.of(
            "espeak_disponible",     espeakDisponible,
            "elevenlabs_disponible", elevenlabsDisponible,
            "metodo_activo",         elevenlabsDisponible ? "ElevenLabs" : espeakDisponible ? "espeak-ng" : "Web Speech API",
            "usuarios_configurados", configUsuarios.size()
        );
    }

    private boolean verificarComando(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            p.waitFor();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
