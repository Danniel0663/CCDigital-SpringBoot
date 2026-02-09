package co.edu.unbosque.ccdigital.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de configuración para la ejecución de herramientas externas del proyecto
 * (integraciones con Fabric e Indy).
 *
 * <p>Las propiedades se mapean desde la configuración de Spring (application.properties o application.yml)
 * usando el prefijo {@code ccdigital.tools}.</p>
 *
 * <p>Estructura esperada (referencial):</p>
 * <pre>
 * ccdigital.tools.fabric.workdir=...
 * ccdigital.tools.fabric.script=...
 * ccdigital.tools.indy.workdir=...
 * ccdigital.tools.indy.venv-activate=...
 * ccdigital.tools.indy.script=...
 * </pre>
 *
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "ccdigital.tools")
public class ExternalToolsProperties {

    private Fabric fabric = new Fabric();
    private Indy indy = new Indy();

    /**
     * Obtiene la configuración para Fabric.
     *
     * @return configuración de Fabric
     */
    public Fabric getFabric() {
        return fabric;
    }

    /**
     * Establece la configuración para Fabric.
     *
     * @param fabric configuración de Fabric
     */
    public void setFabric(Fabric fabric) {
        this.fabric = fabric;
    }

    /**
     * Obtiene la configuración para Indy.
     *
     * @return configuración de Indy
     */
    public Indy getIndy() {
        return indy;
    }

    /**
     * Establece la configuración para Indy.
     *
     * @param indy configuración de Indy
     */
    public void setIndy(Indy indy) {
        this.indy = indy;
    }

    /**
     * Bloque de propiedades para ejecución de scripts asociados a Fabric.
     *
     * @since 1.0.0
     */
    public static class Fabric {

        /**
         * Directorio de trabajo donde se encuentra el script a ejecutar.
         */
        private String workdir;

        /**
         * Nombre del script a ejecutar.
         */
        private String script;

        /**
         * Obtiene el directorio de trabajo del script.
         *
         * @return directorio de trabajo
         */
        public String getWorkdir() {
            return workdir;
        }

        /**
         * Establece el directorio de trabajo del script.
         *
         * @param workdir directorio de trabajo
         */
        public void setWorkdir(String workdir) {
            this.workdir = workdir;
        }

        /**
         * Obtiene el nombre del script.
         *
         * @return nombre del script
         */
        public String getScript() {
            return script;
        }

        /**
         * Establece el nombre del script.
         *
         * @param script nombre del script
         */
        public void setScript(String script) {
            this.script = script;
        }
    }

    /**
     * Bloque de propiedades para ejecución de scripts asociados a Indy.
     *
     * @since 1.0.0
     */
    public static class Indy {

        /**
         * Directorio del proyecto o entorno Python donde se ejecutará el script.
         */
        private String workdir;

        /**
         * Comando de activación del entorno virtual (venv) previo a la ejecución del script.
         */
        private String venvActivate;

        /**
         * Nombre del script Python a ejecutar.
         */
        private String script;

        /**
         * Obtiene el directorio de trabajo del proyecto.
         *
         * @return directorio de trabajo
         */
        public String getWorkdir() {
            return workdir;
        }

        /**
         * Establece el directorio de trabajo del proyecto.
         *
         * @param workdir directorio de trabajo
         */
        public void setWorkdir(String workdir) {
            this.workdir = workdir;
        }

        /**
         * Obtiene el comando de activación del entorno virtual.
         *
         * @return comando de activación
         */
        public String getVenvActivate() {
            return venvActivate;
        }

        /**
         * Establece el comando de activación del entorno virtual.
         *
         * @param venvActivate comando de activación
         */
        public void setVenvActivate(String venvActivate) {
            this.venvActivate = venvActivate;
        }

        /**
         * Obtiene el nombre del script Python.
         *
         * @return script Python
         */
        public String getScript() {
            return script;
        }

        /**
         * Establece el nombre del script Python.
         *
         * @param script script Python
         */
        public void setScript(String script) {
            this.script = script;
        }
    }
}
