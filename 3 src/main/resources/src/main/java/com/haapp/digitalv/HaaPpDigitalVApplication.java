package com.haapp.digitalv.controller;

import com.haapp.digitalv.service.SincronizacionService;
import com.haapp.digitalv.service.VozLocalService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;

import java.util.*;

@RestController
@CrossOrigin(origins = "*")
public class HaaPpController {

    private final SincronizacionService sync;
    private final VozLocalService voz;

    @Value("${hz.kuhul:12.3}")
    private double hzKuhul;

    // ── Datos estáticos ──
    private static final List<Map<String, Object>> RUTAS_VAIVEN = List.of(
        ruta("R1","Ruta 1 — Centro · Periférico Norte","#00ffc8","15 min",
            List.of(paradero("Centro Histórico",20.9674,-89.6233),paradero("Paseo Montejo",21.0000,-89.6200),paradero("Plaza Las Américas",21.0230,-89.6150),paradero("Periférico Norte",21.0450,-89.6100))),
        ruta("R2","Ruta 2 — Gran Plaza · Caucel","#f5c518","20 min",
            List.of(paradero("Gran Plaza",20.9800,-89.6400),paradero("Plaza Fiesta",20.9750,-89.6500),paradero("Xcumpich",20.9700,-89.6700),paradero("Caucel",20.9650,-89.7000))),
        ruta("R3","Ruta 3 — Itzimná · García Ginerés","#e74c3c","18 min",
            List.of(paradero("Itzimná",21.0100,-89.6050),paradero("Col. México",20.9950,-89.6100),paradero("García Ginerés",20.9850,-89.6200),paradero("Centro",20.9674,-89.6233))),
        ruta("R4","Ruta 4 — Mulsay · Centro","#9b59b6","25 min",
            List.of(paradero("Mulsay",20.9400,-89.6600),paradero("Sambula",20.9500,-89.6400),paradero("Av. Itzáes",20.9600,-89.6300),paradero("Centro",20.9674,-89.6233))),
        ruta("R5","Ruta 5 — Periférico Sur · Dzidzantún","#3498db","30 min",
            List.of(paradero("Terminal Sur",20.9200,-89.6233),paradero("Periférico Oriente",20.9400,-89.5800),paradero("Fracc. del Parque",20.9600,-89.5600),paradero("Dzidzantún",21.0300,-89.5500))),
        ruta("R6","Ruta 6 — Ciudad Caucel · Temozón","#2ecc71","20 min",
            List.of(paradero("Ciudad Caucel",20.9600,-89.7200),paradero("Aeropuerto",20.9350,-89.6577),paradero("Temozón Norte",21.0500,-89.5800),paradero("Plaza Altabrisa",21.0200,-89.5900)))
    );

    private static final List<Map<String, Object>> ZONAS_KUHUL = List.of(
        zona("Mérida Centro",20.9674,-89.6233,12.3,"urbana"),
        zona("Chichén Itzá",20.6843,-88.5678,13.1,"arqueologica"),
        zona("Uxmal",20.3597,-89.7713,12.8,"arqueologica"),
        zona("Dzibilchaltún",21.0910,-89.5910,12.5,"arqueologica"),
        zona("Cenote Ik Kil",20.6560,-88.5660,11.8,"cenote"),
        zona("Celestún",20.8590,-90.4030,10.9,"costa"),
        zona("Progreso",21.2830,-89.6670,11.2,"costa"),
        zona("Valladolid",20.6890,-88.2020,12.7,"colonial"),
        zona("Izamal",20.9320,-89.0180,13.0,"colonial"),
        zona("Ek Balam",20.8900,-88.1360,12.9,"arqueologica"),
        zona("Mayapán",20.6300,-89.4600,12.4,"arqueologica"),
        zona("Mérida Oriente",20.9800,-89.5800,12.1,"urbana")
    );

    // Sueños en memoria (persistidos en H2 en versión extendida)
    private final List<Map<String, Object>> suenosDB = new ArrayList<>();

    public HaaPpController(SincronizacionService sync, VozLocalService voz) {
        this.sync = sync;
        this.voz  = voz;
    }

    // ════════════════════════════════════════
    //  HEALTH
    // ════════════════════════════════════════
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status",      "OK",
            "app",         "HaaPpDigitalV v1.0 — Java Spring Boot",
            "hz",          hzKuhul,
            "voz",         voz.estado(),
            "red_sofi",    sync.getEstadoRed(),
            "modulos",     List.of("mapa","vaiven","frecuencias","suenos","tiktok","vendedor","voz_local","sync"),
            "frase",       "Tecnología con alma maya",
            "timestamp",   System.currentTimeMillis()
        );
    }

    // ════════════════════════════════════════
    //  MAPA K'UHUL
    // ════════════════════════════════════════
    @GetMapping("/api/mapa/zonas")
    public Map<String, Object> zonas() {
        return Map.of("zonas", ZONAS_KUHUL, "total", ZONAS_KUHUL.size(), "hz_base", hzKuhul);
    }

    @GetMapping("/api/mapa/rutas")
    public Map<String, Object> rutasMapa() {
        return Map.of("rutas", RUTAS_VAIVEN, "total", RUTAS_VAIVEN.size());
    }

    @GetMapping("/api/mapa/cercano")
    public Map<String, Object> zonaCercana(@RequestParam double lat, @RequestParam double lng) {
        Map<String, Object> cercana = ZONAS_KUHUL.stream()
            .min(Comparator.comparingDouble(z -> distancia(lat, lng,
                (double) z.get("lat"), (double) z.get("lng"))))
            .orElse(ZONAS_KUHUL.get(0));
        return Map.of("zona", cercana, "distancia_km",
            String.format("%.2f", distancia(lat, lng, (double) cercana.get("lat"), (double) cercana.get("lng"))));
    }

    // ════════════════════════════════════════
    //  VA Y VEN
    // ════════════════════════════════════════
    @GetMapping("/api/vaiven/rutas")
    public Map<String, Object> rutasVaiven() {
        return Map.of("rutas", RUTAS_VAIVEN, "total", RUTAS_VAIVEN.size());
    }

    @GetMapping("/api/vaiven/ruta/{id}")
    public ResponseEntity<?> rutaDetalle(@PathVariable String id) {
        return RUTAS_VAIVEN.stream()
            .filter(r -> r.get("id").equals(id.toUpperCase()))
            .findFirst()
            .map(r -> {
                Map<String, Object> resp = new HashMap<>(r);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> ps = (List<Map<String, Object>>) r.get("paraderos");
                List<Map<String, Object>> conTiempo = new ArrayList<>();
                for (int i = 0; i < ps.size(); i++) {
                    Map<String, Object> p = new HashMap<>(ps.get(i));
                    p.put("orden", i + 1);
                    p.put("tiempo_estimado", (i + 1) * (new Random().nextInt(5) + 8) + " min");
                    conTiempo.add(p);
                }
                resp.put("paraderos", conTiempo);
                return ResponseEntity.ok(resp);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/vaiven/cercana")
    public Map<String, Object> paraderosCercanos(@RequestBody Map<String, Double> body) {
        double lat = body.getOrDefault("lat", 20.9674);
        double lng = body.getOrDefault("lng", -89.6233);
        List<Map<String, Object>> resultado = new ArrayList<>();
        for (Map<String, Object> ruta : RUTAS_VAIVEN) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> paraderos = (List<Map<String, Object>>) ruta.get("paraderos");
            paraderos.stream()
                .min(Comparator.comparingDouble(p -> distancia(lat, lng, (double) p.get("lat"), (double) p.get("lng"))))
                .ifPresent(p -> {
                    Map<String, Object> r = new HashMap<>();
                    r.put("ruta", ruta.get("nombre"));
                    r.put("id",   ruta.get("id"));
                    r.put("color", ruta.get("color"));
                    r.put("paradero", p.get("nombre"));
                    r.put("distancia_km", String.format("%.2f",
                        distancia(lat, lng, (double) p.get("lat"), (double) p.get("lng"))));
                    resultado.add(r);
                });
        }
        resultado.sort(Comparator.comparing(r -> (String) r.get("distancia_km")));
        return Map.of("rutas_cercanas", resultado.subList(0, Math.min(3, resultado.size())));
    }

    // ════════════════════════════════════════
    //  FRECUENCIAS
    // ════════════════════════════════════════
    @PostMapping("/api/frecuencias/analizar")
    public Map<String, Object> analizarFrecuencia(@RequestBody Map<String, Object> body) {
        double hz = Double.parseDouble(body.getOrDefault("hz_detectado", 0).toString());
        double diff = Math.abs(hz - hzKuhul);
        String estado, recomendacion;
        if (diff < 0.5)      { estado = "RESONANCIA K'UHUL";  recomendacion = "Estás en sincronía con la frecuencia maya. Momento ideal para meditar o crear."; }
        else if (diff < 2.0) { estado = "AFINANDO";           recomendacion = "Cerca de la resonancia. Respira profundo, reduce estímulos externos."; }
        else if (hz < 8)     { estado = "DELTA / SUEÑO";      recomendacion = "Frecuencia de descanso profundo. Tu cuerpo necesita recuperarse."; }
        else if (hz < 13)    { estado = "ALPHA / CALMA";       recomendacion = "Estado de calma consciente. Ideal para aprender o planear."; }
        else                 { estado = "BETA / ACTIVO";       recomendacion = "Alta actividad mental. Bueno para tareas que requieren enfoque."; }
        double coherencia = Math.max(0, Math.min(1, 1 - diff / hzKuhul));

        // Aprender este patrón
        sync.agregarPatron("frecuencia_usuario", Map.of("hz", hz, "estado", estado), "sensor_movil");

        return Map.of("hz_detectado", hz, "hz_kuhul", hzKuhul,
            "estado", estado, "recomendacion", recomendacion,
            "coherencia", String.format("%.3f", coherencia));
    }

    // ════════════════════════════════════════
    //  SUEÑOS
    // ════════════════════════════════════════
    @PostMapping("/api/suenos/registrar")
    public Map<String, Object> registrarSueno(@RequestBody Map<String, Object> body) {
        String descripcion = (String) body.getOrDefault("descripcion", "");
        String userId      = (String) body.getOrDefault("usuario_id", "anonimo");
        if (descripcion.isBlank()) return Map.of("error", "descripcion requerida");

        Map<String, Object> sueno = new HashMap<>();
        sueno.put("id",          "S-" + System.currentTimeMillis());
        sueno.put("descripcion", descripcion);
        sueno.put("usuario_id",  userId);
        sueno.put("fecha",       new Date().toInstant().toString());
        sueno.put("hz",          hzKuhul);
        sueno.put("analisis",    analizarSueno(descripcion));
        suenosDB.add(sueno);

        sync.agregarPatron("sueno_usuario", Map.of("usuario", userId, "palabras", descripcion.split(" ").length), "app_haapp");
        return sueno;
    }

    @GetMapping("/api/suenos/{userId}")
    public Map<String, Object> obtenerSuenos(@PathVariable String userId) {
        List<Map<String, Object>> suenos = suenosDB.stream()
            .filter(s -> userId.equals(s.get("usuario_id")))
            .toList();
        return Map.of("suenos", suenos, "total", suenos.size());
    }

    private Map<String, Object> analizarSueno(String texto) {
        String t = texto.toLowerCase();
        List<Map<String, String>> simbolos = new ArrayList<>();
        if (t.contains("agua") || t.contains("cenote"))    simbolos.add(Map.of("simbolo","Agua","significado","Purificación y renacimiento maya"));
        if (t.contains("serpiente") || t.contains("kukulcan")) simbolos.add(Map.of("simbolo","Kukulcán","significado","Transformación y sabiduría"));
        if (t.contains("jaguar"))                           simbolos.add(Map.of("simbolo","Jaguar","significado","Poder del inframundo Xibalbá"));
        if (t.contains("luz") || t.contains("sol"))        simbolos.add(Map.of("simbolo","Kinich Ahau","significado","El dios sol — claridad y propósito"));
        if (t.contains("pirámide") || t.contains("templo")) simbolos.add(Map.of("simbolo","Pirámide","significado","Conexión entre planos de conciencia"));
        if (t.contains("volar") || t.contains("vuelo"))    simbolos.add(Map.of("simbolo","Vuelo","significado","Ascenso al plano 5 de conciencia"));
        String mensaje = simbolos.isEmpty()
            ? "Sueño de procesamiento cotidiano. Sigue registrando para encontrar patrones."
            : "Tu subconsciente habla en símbolos mayas: " + simbolos.stream().map(s -> s.get("simbolo")).reduce((a,b) -> a+", "+b).orElse("");
        return Map.of("simbolos_mayas", simbolos, "mensaje", mensaje);
    }

    // ════════════════════════════════════════
    //  CONTENIDO PRO — TIKTOK
    // ════════════════════════════════════════
    @PostMapping("/api/contenido/tiktok")
    public ResponseEntity<?> generarTiktok(@RequestBody Map<String, Object> body) {
        String tier = (String) body.getOrDefault("tier", "free");
        if ("free".equals(tier)) return ResponseEntity.status(403).body(Map.of("error","Función Pro","upgrade_url","/planes"));
        String tema  = (String) body.getOrDefault("tema", "");
        String nicho = (String) body.getOrDefault("nicho", "general");
        int dur      = Integer.parseInt(body.getOrDefault("duracion", 60).toString());

        String hook = generarHook(tema);
        List<String> hashtags = generarHashtags(tema, nicho);

        sync.agregarPatron("contenido_tiktok", Map.of("tema", tema, "nicho", nicho), "generador_java");

        return ResponseEntity.ok(Map.of(
            "tema", tema, "hook", hook, "hashtags", hashtags,
            "estructura", Map.of(
                "0-3s",  "HOOK: " + hook,
                "3-15s", "El problema que resuelves",
                "15-" + (dur-10) + "s", "Tu solución única con prueba",
                (dur-10) + "-" + (dur-5) + "s", "Prueba social o resultado",
                (dur-5) + "-" + dur + "s", "CTA: Sígueme para más"
            ),
            "tip_algoritmo", "Publica entre 7-9pm CST para mayor alcance en Yucatán y México",
            "motor", "HaaPpDigitalV Java v1.0"
        ));
    }

    // ════════════════════════════════════════
    //  CONTENIDO PRO — VENDEDOR
    // ════════════════════════════════════════
    @PostMapping("/api/contenido/vendedor")
    public ResponseEntity<?> generarVendedor(@RequestBody Map<String, Object> body) {
        String tier     = (String) body.getOrDefault("tier", "free");
        if ("free".equals(tier)) return ResponseEntity.status(403).body(Map.of("error","Función Pro"));
        String producto = (String) body.getOrDefault("producto", "");
        String precio   = (String) body.getOrDefault("precio", "Consultar");

        sync.agregarPatron("contenido_vendedor", Map.of("producto", producto), "generador_java");

        return ResponseEntity.ok(Map.of(
            "producto", producto,
            "titular",  "¿Buscas " + producto + "? Aquí está la solución 🔥",
            "copy",     producto + " — calidad garantizada. Solo $" + precio + " MXN. Lleva el tuyo hoy.",
            "cta",      List.of("Escríbeme al WhatsApp 👇","Link en bio 🔗","Comenta INFO para más detalles"),
            "hashtags", generarHashtags(producto, "ventas"),
            "plataformas", Map.of(
                "facebook",  "3-5 párrafos con foto",
                "instagram", "150 chars + emojis + stories",
                "tiktok",    "Script 60s con demo",
                "whatsapp",  "Mensaje directo 2 líneas"
            )
        ));
    }

    // ════════════════════════════════════════
    //  VOZ LOCAL
    // ════════════════════════════════════════
    @PostMapping("/api/voz/generar")
    public Map<String, Object> generarVoz(@RequestBody Map<String, Object> body) {
        String texto  = (String) body.getOrDefault("texto", "");
        String userId = (String) body.getOrDefault("userId", "default");
        if (texto.isBlank()) return Map.of("error", "texto requerido");
        return voz.generarVoz(texto, userId);
    }

    @PostMapping("/api/voz/configurar")
    public Map<String, Object> configurarVoz(@RequestBody Map<String, Object> body) {
        String userId = (String) body.getOrDefault("userId", "default");
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) body.getOrDefault("config", Map.of());
        return voz.configurarVoz(userId, config);
    }

    @GetMapping("/api/voz/estado")
    public Map<String, Object> estadoVoz() { return voz.estado(); }

    // ════════════════════════════════════════
    //  SINCRONIZACIÓN — ENDPOINTS
    // ════════════════════════════════════════
    @PostMapping("/api/aprender_masivo")
    public Map<String, Object> aprenderMasivo(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> patrones = (List<Map<String, Object>>) body.getOrDefault("patrones", List.of());
        String fuente = (String) body.getOrDefault("fuente", "desconocida");
        return sync.aprenderMasivo(patrones, fuente);
    }

    @GetMapping("/api/red-sofi")
    public Map<String, Object> estadoRed() { return sync.getEstadoRed(); }

    @GetMapping("/api/estado")
    public Map<String, Object> estadoCompleto() {
        return Map.of(
            "app",       "HaaPpDigitalV Java v1.0",
            "hz",        hzKuhul + Math.sin(System.currentTimeMillis() / 10000.0) * 0.05,
            "version",   "java-1.0",
            "patrones",  sync.getPatrones().size(),
            "red",       sync.getEstadoRed(),
            "timestamp", new Date().toInstant().toString()
        );
    }

    // ════════════════════════════════════════
    //  PLANES
    // ════════════════════════════════════════
    @GetMapping("/api/planes")
    public Map<String, Object> planes() {
        return Map.of("planes", List.of(
            Map.of("id","free",   "nombre","Free",   "precio",0,   "moneda","MXN","funciones",List.of("Mapa K'uhul","Chat","Sueños","Frecuencias","Va y Ven")),
            Map.of("id","pro",    "nombre","Pro",    "precio",99,  "moneda","MXN","funciones",List.of("Todo Free","TikTok generator","Kit vendedor","Voz local","Sin anuncios")),
            Map.of("id","kuhul",  "nombre","K'uhul", "precio",299, "moneda","MXN","funciones",List.of("Todo Pro","Análisis frecuencial IA","Agente de ventas","Soporte prioritario"))
        ));
    }

    // ════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════
    private static Map<String, Object> ruta(String id, String nombre, String color, String freq, List<Map<String, Object>> paraderos) {
        return Map.of("id",id,"nombre",nombre,"color",color,"frecuencia",freq,"paraderos",paraderos);
    }
    private static Map<String, Object> paradero(String nombre, double lat, double lng) {
        return Map.of("nombre",nombre,"lat",lat,"lng",lng);
    }
    private static Map<String, Object> zona(String nombre, double lat, double lng, double hz, String tipo) {
        return Map.of("nombre",nombre,"lat",lat,"lng",lng,"hz",hz,"tipo",tipo);
    }
    private double distancia(double lat1, double lng1, double lat2, double lng2) {
        return Math.sqrt(Math.pow(lat1-lat2,2) + Math.pow(lng1-lng2,2)) * 111;
    }
    private String generarHook(String tema) {
        String[] hooks = {
            "¿Sabías que " + tema + " puede cambiar tu vida? 🤯",
            "El secreto de " + tema + " que nadie te cuenta 🔥",
            "POV: Descubres " + tema + " por primera vez 😱",
            "3 cosas sobre " + tema + " que necesitas saber YA ⚡"
        };
        return hooks[new Random().nextInt(hooks.length)];
    }
    private List<String> generarHashtags(String tema, String nicho) {
        List<String> tags = new ArrayList<>(List.of("#HaaPpDigitalV","#Yucatan","#Merida","#TecnologiaMaya"));
        Arrays.stream(tema.split(" ")).limit(2).forEach(w -> tags.add("#" + w.replaceAll("[^a-zA-ZáéíóúÁÉÍÓÚ]","")));
        if (nicho != null && !nicho.isBlank()) tags.add("#" + nicho.replaceAll("\\s",""));
        return tags.subList(0, Math.min(8, tags.size()));
    }
}

