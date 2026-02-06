package co.edu.unbosque.ccdigital.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propiedades para ejecutar herramientas externas (Fabric / Indy).
 * Se mapean desde application.properties con el prefijo "ccdigital.tools".
 */
@Component
@ConfigurationProperties(prefix = "ccdigital.tools")
public class ExternalToolsProperties {

    private Fabric fabric = new Fabric();
    private Indy indy = new Indy();

    public Fabric getFabric() {
        return fabric;
    }

    public void setFabric(Fabric fabric) {
        this.fabric = fabric;
    }

    public Indy getIndy() {
        return indy;
    }

    public void setIndy(Indy indy) {
        this.indy = indy;
    }

    // =======================
    // Sub-bloque: FABRIC
    // =======================
    public static class Fabric {
        /**
         * Directorio donde está el script sync-db-to-ledger.js
         * Ejemplo: /home/ccdigital/fabric/fabric-samples/test-network/client
         */
        private String workdir;

        /**
         * Nombre del script.
         * Ejemplo: sync-db-to-ledger.js
         */
        private String script;

        public String getWorkdir() {
            return workdir;
        }

        public void setWorkdir(String workdir) {
            this.workdir = workdir;
        }

        public String getScript() {
            return script;
        }

        public void setScript(String script) {
            this.script = script;
        }
    }

    // =======================
    // Sub-bloque: INDY
    // =======================
    public static class Indy {
        /**
         * Directorio del proyecto python:
         * Ejemplo: /home/ccdigital/cdigital-indy-python
         */
        private String workdir;

        /**
         * Comando para activar venv:
         * Ejemplo: source venv/bin/activate
         */
        private String venvActivate;

        /**
         * Script python:
         * Ejemplo: issue_credentials_from_db.py
         */
        private String script;

        public String getWorkdir() {
            return workdir;
        }

        public void setWorkdir(String workdir) {
            this.workdir = workdir;
        }

        public String getVenvActivate() {
            return venvActivate;
        }

        public void setVenvActivate(String venvActivate) {
            this.venvActivate = venvActivate;
        }

        public String getScript() {
            return script;
        }

        public void setScript(String script) {
            this.script = script;
        }
    }
}
