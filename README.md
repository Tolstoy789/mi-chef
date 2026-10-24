# Mi Chefcito

App Android (Kotlin + Jetpack Compose + Material 3) que sugiere recetas con los ingredientes que tienes en casa (dando prioridad a los que caducan pronto), usando Gemma en el propio dispositivo con MediaPipe `LlmInference`. No necesita internet.

## Cómo abrirla

1. Abre esta carpeta en Android Studio (Ladybug 2024.2 o más reciente) y deja que sincronice Gradle.
2. Necesitas un teléfono real con al menos 6 GB de RAM. El emulador no sirve bien para inferencia LLM.

## El modelo (paso obligatorio)

El modelo pesa unos 500 MB o más, así que no va dentro del APK. Recomiendo **Gemma 3 1B IT int4** (`gemma3-1b-it-int4.task`), que se descarga de Hugging Face en `litert-community/Gemma3-1B-IT` (hay que aceptar la licencia de Gemma).

Descárgalo en el teléfono (por ejemplo, en la carpeta Descargas). La primera vez que abras la app verás la pantalla «Elige el modelo de IA»: pulsa «Elegir archivo del modelo» y selecciona el `.task`. La app lo copia una sola vez a su almacenamiento interno (después puedes borrar el original) y lo recuerda. Para cambiarlo más tarde, pulsa «Cambiar» junto al nombre del modelo.

Para desarrollo también sigue funcionando copiarlo con adb:

```bash
adb shell mkdir -p /data/local/tmp/llm
adb push gemma3-1b-it-int4.task /data/local/tmp/llm/model.task
```

## Cómo funciona

- **`MainActivity.kt`**: la UI en Compose. Formulario (Ingrediente + Días con teclado numérico), lista ordenada de menor a mayor caducidad con los urgentes resaltados, botón flotante "Generar Receta", indicador de carga y un diálogo con la receta.
- **`ChefViewModel.kt`**: el estado (`StateFlow`), la carga de `LlmInference` en `Dispatchers.IO`, el filtro de ingredientes con 3 días o menos, el prompt estricto de chef y la inferencia en una corrutina. Cada receta usa una `LlmInferenceSession` nueva para no mezclar contexto.
- **`app/build.gradle.kts`**: todas las dependencias, con versiones exactas.

## Funciones

- Despensa guardada en el teléfono. La fecha de caducidad es opcional; los que la tienen se ordenan primero. Los urgentes (3 días o menos) y los caducados se marcan con colores, y hay un botón para quitar los caducados.
- Al eliminar un ingrediente aparece «Deshacer».
- Preferencias para la receta: rápida, vegetariana o sin gluten.
- La receta se genera con los ingredientes de la lista (los caducados se dejan fuera) y los que caducan pronto son obligatorios.
- La receta se escribe en tiempo real en una pantalla completa, con botones para compartir o guardar la receta en PDF, pedir otra receta o marcarla como cocinada (quita de la despensa lo que usó).
- Icono adaptable y pantalla de inicio animada (sartén con brote).

## Decisiones tomadas

- `tasks-genai:0.10.24`, AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, minSdk 26 (para el icono adaptable), compileSdk 35.
- Backend CPU por defecto, que funciona en todos los teléfonos. El manifest declara las librerías OpenCL opcionales por si luego quieres probar GPU.
- Los ingredientes se guardan en el teléfono (SharedPreferences) con su fecha de caducidad, así que la cuenta de días baja sola cada día.

Nota: Google está migrando la inferencia LLM de MediaPipe hacia LiteRT-LM. Esta versión de MediaPipe sigue funcionando; si Android Studio te ofrece subir `tasks-genai`, revisa sus notas de versión antes.
