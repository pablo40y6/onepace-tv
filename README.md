# One Pace TV

Código de la aplicación para Android TV y Google TV. Esta rama incluye el reproductor, el catálogo de arranque, las carátulas, los subtítulos añadidos y los tiempos para saltar la intro. [Historial de versiones](CHANGELOG.md)

## Compilar en Windows

Instala PowerShell, Node.js, un JDK 21 y las herramientas de Android. La compilación de la 1.1.1 usa Node.js 24, Android SDK Platform 34 y Android SDK Build-Tools 35.0.0.

Prepara un directorio de herramientas local con este contenido.

```text
OnePaceTV-AndroidToolchain/
  jdk/<carpeta del JDK>/bin/java.exe
  platform/<carpeta de la plataforma>/android.jar
  build-tools/<carpeta de Build-Tools>/aapt2.exe
```

Desde la raíz de esta rama, ejecuta la compilación con la ruta de tu directorio de herramientas.

```powershell
.\build.ps1 -Toolchain 'C:\ruta\OnePaceTV-AndroidToolchain' -Apk "$PWD\OnePaceTV-local.apk"
```

El APK se puede compilar con el catálogo de arranque incluido. El parámetro opcional `-Website` admite un checkout local de `onepace-facil-en` como fuente alternativa para ese catálogo.

## Editar y probar

- `src/` contiene la interfaz, el reproductor de Android y la lógica de actualizaciones.
- `player.js` controla el reproductor que se abre desde las listas de Pixeldrain.
- `res/` y `art/` contienen el icono, las portadas y la pantalla de idioma.
- `bundled-catalog.json`, `spanish-subtitles.json`, `caption-overrides.json` e `intro-timings.json` contienen el catálogo de inicio y la configuración de reproducción. Las pistas de texto añadidas están en los archivos `*.es-419.json`.

Ejecuta las pruebas del reproductor de Pixeldrain con `node --test player.test.cjs`. Las pruebas de las clases Java están en `tests/`.

## Firma y publicaciones

El script usa el archivo local `onepace-tv-test.p12` del directorio de herramientas y genera una clave de desarrollo cuando ese archivo falta. Para actualizar instalaciones existentes, usa la clave original de la app, conserva el identificador del paquete y aumenta `versionCode` en `AndroidManifest.xml`. Guarda la clave de firma fuera del repositorio.

El actualizador reconoce etiquetas como `apk-v1.1.1` y el archivo `OnePaceTV-1.1.1.apk` adjunto a la publicación. Los datos que se consultan al abrir la app se mantienen en la rama [tv-catalog](https://github.com/pablo40y6/onepace-tv/tree/tv-catalog).
