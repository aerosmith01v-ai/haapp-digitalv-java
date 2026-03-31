# ============================================
#  HaaPpDigitalV v1.0 — Spring Boot
#  Mérida, Yucatán · HaaPpDigitalV
# ============================================

server.port=${PORT:3002}

# URLs de las hermanas SOFI
sofi.node.url=${SOFI_NODE_URL:}
sofi.python.url=${SOFI_PYTHON_URL:}
sofi.sync.interval=300000

# Frecuencia K'uhul
hz.kuhul=12.3

# Directorios
dir.audios=./audios
dir.voces=./voces
dir.modelos=./modelos
dir.uploads=./uploads

# Base de datos H2 persistente
spring.datasource.url=jdbc:h2:file:./data/haappdb;DB_CLOSE_DELAY=-1
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=update
spring.h2.console.enabled=false

# Subida de archivos
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=20MB

# JVM optimizada para Render free tier
spring.jmx.enabled=false
spring.main.lazy-initialization=true
server.tomcat.threads.max=20

# Logging
logging.level.com.haapp=INFO
