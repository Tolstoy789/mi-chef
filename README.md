# Chef de Sobras

App Android (Kotlin + Jetpack Compose + Material 3) que sugiere recetas con los ingredientes que caducan en 3 días o menos, usando Gemma en el propio dispositivo con MediaPipe `LlmInference`. No necesita internet.

## Cómo abrirla

1. Abre esta carpeta en Android Studio (Ladybug 2024.2 o más reciente) y deja que sincronice Gradle.
2. Necesitas un teléfono real con al menos 6 GB de RAM. El emulador no sirve bien para inferencia LLM.

## El modelo (paso obligatorio)

El modelo pesa unos 500 MB o más, así que no va dentro del APK. Recomiendo **Gemma 3 1B IT int4** (`gemma3-1b-it-int4.task`), que se descarga de Hugging Face en `litert-community/Gemma3-1B-IT` (hay que aceptar la licencia de Gemma).

Cópialo al teléfono con el nombre `model.task`:

```bash
adb shell mkdir -p /data/local/tmp/llm
adb push gemma3-1b-it-int4.task /data/local/tmp/llm/model.task
```

La app busca el modelo en este orden:

1. `/data/local/tmp/llm/model.task` (lo cómodo para desarrollo).
2. `/sdcard/Android/data/com.chefdesobras.app/files/model.task` (útil si la app ya está instalada y no es debug).
3. `app/src/main/assets/model.task`, que copia una vez al almacenamiento interno. Solo conviene con modelos pequeños, porque infla el APK.

Si no lo encuentra, la app muestra el aviso con las rutas y un botón "Reintentar".

## Cómo funciona

- **`MainActivity.kt`**: la UI en Compose. Formulario (Ingrediente + Días con teclado numérico), lista ordenada de menor a mayor caducidad con los urgentes resaltados, botón flotante "Generar Receta", indicador de carga y un diálogo con la receta.
- **`ChefViewModel.kt`**: el estado (`StateFlow`), la carga de `LlmInference` en `Dispatchers.IO`, el filtro de ingredientes con 3 días o menos, el prompt estricto de chef y la inferencia en una corrutina. Cada receta usa una `LlmInferenceSession` nueva para no mezclar contexto.
- **`app/build.gradle.kts`**: todas las dependencias, con versiones exactas.

## Funciones

- Despensa guardada en el teléfono, ordenada por fecha de caducidad. Los urgentes (3 días o menos) y los caducados se marcan con colores, y hay un botón para quitar los caducados.
- Al eliminar un ingrediente aparece «Deshacer».
- Preferencias para la receta: rápida, vegetariana o sin gluten.
- El chef usa obligatoriamente los ingredientes urgentes y puede apoyarse en el resto de la despensa.
- La receta se escribe en tiempo real en una pantalla completa, con botones para compartir, pedir otra receta o marcarla como cocinada (quita de la despensa lo que usó).
- Icono adaptable y pantalla de inicio animada (sartén con brote).

## Decisiones tomadas

- `tasks-genai:0.10.24`, AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, minSdk 26 (para el icono adaptable), compileSdk 35.
- Backend CPU por defecto, que funciona en todos los teléfonos. El manifest declara las librerías OpenCL opcionales por si luego quieres probar GPU.
- Los ingredientes se guardan en el teléfono (SharedPreferences) con su fecha de caducidad, así que la cuenta de días baja sola cada día.

Nota: Google está migrando la inferencia LLM de MediaPipe hacia LiteRT-LM. Esta versión de MediaPipe sigue funcionando; si Android Studio te ofrece subir `tasks-genai`, revisa sus notas de versión antes.
