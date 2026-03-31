ackage com.haapp.digitalv.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.file.*;
import java.util.*;

@Service
public class VozLocalService {

    private static final Logger log = LoggerFactory.getLogger(VozLocalService.class);

    @Value("${dir.audios:./audios}")
    private String dirAudios;

    @Value("${server.port:3002}")
    private String port;

    private boolean espeakDisponible = false;

    private final Map<String, Map<String, Object>> configUsuarios = new HashMap<>();

    public VozLocalService() {
        new File("./audios").mkdirs();
        espeakDisponible = verificarComando("espeak-ng --version");
        log.info("Voz espeak disponible: {}", espeakDisponible);
    }

    public Map<String, Object> generarVoz(String texto, String userId) {
        String audioId = "voz_" + System.currentTimeMillis();
        String apiKey  = System.getenv("ELEVENLABS_API_KEY");
        String voiceId = System.getenv("ELEVENLABS_VOICE_ID");
        if (apiKey != null && voiceId != null) {
            Map<String, Object> r = generarConElevenLabs(texto, audioId, apiKey, voiceId);
            if (r != null) return r;
        }
        if (espeakDisponible) {
            Map<String, Object> r = generarConEspeak(texto, audioId);
            if (r != null) return r;
        }
        return Map.of("audioId", audioId, "metodo", "web_speech_api", "texto", texto);
    }

    private Map<String, Object> generarConEspeak(String texto, String audioId) {
        try {
            String wav = dirAudios + "/" + audioId + ".wav";
            Process proc = new ProcessBuilder("espeak-ng", "-v", "es-la", "-s", "140", "-w", wav, texto)
                .redirectErrorStream(true).start();
            proc.waitFor();
            if (new File(wav).exists()) {
                String base = System.getenv("MI_URL") != null ? System.getenv("MI_URL") : "http://localhost:" + port;
                return Map.of("audioId", audioId, "url", base + "/audios/" + audioId + ".wav", "metodo", "espeak-ng");
            }
        } catch (Exception e) { log.warn("espeak fallo: {}", e.getMessage()); }
        return null;
    }

    private Map<String, Object> generarConElevenLabs(String texto, String audioId, String apiKey, String voiceId) {
        try {
            String body = "{\"text\":\"" + texto.replace("\"","\\\"") + "\",\"model_id\":\"eleven_multilingual_v2\",\"voice_settings\":{\"stability\":0.5,\"similarity_boost\":0.85}}";
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("https://api.elevenlabs.io/v1/text-to-speech/" + voiceId))
                .header("xi-api-key", apiKey).header("Content-Type", "application/json").header("Accept", "audio/mpeg")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                .timeout(java.time.Duration.ofSeconds(20)).build();
            java.net.http.HttpResponse<byte[]> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() == 200) {
                String mp3 = dirAudios + "/" + audioId + ".mp3";
                Files.write(Paths.get(mp3), resp.body());
                String base = System.getenv("MI_URL") != null ? System.getenv("MI_URL") : "http://localhost:" + port;
                return Map.of("audioId", audioId, "url", base + "/audios/" + audioId + ".mp3", "metodo", "elevenlabs");
            }
        } catch (Exception e) { log.warn("ElevenLabs fallo: {}", e.getMessage()); }
        return null;
    }

    public Map<String, Object> configurarVoz(String userId, Map<String, Object> config) {
        configUsuarios.put(userId, config);
        return Map.of("ok", true, "userId", userId, "config", config);
    }

    public Map<String, Object> obtenerConfig(String userId) {
        return configUsuarios.getOrDefault(userId, Map.of("velocidad", 1.0, "idioma", "es-MX"));
    }

    public Map<String, Object> estado() {
        return Map.of("espeak_disponible", espeakDisponible, "elevenlabs_disponible", System.getenv("ELEVENLABS_API_KEY") != null);
    }

    private boolean verificarComando(String cmd) {
        try { Runtime.getRuntime().exec(cmd).waitFor(); return true; } catch (Exception e) { return false; }
    }
}
