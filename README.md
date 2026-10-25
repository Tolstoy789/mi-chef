# Mi Chefcito

App Android (Kotlin + Jetpack Compose + Material 3) que sugiere recetas con los ingredientes que tienes en casa (dando prioridad a los que caducan pronto), usando Gemma en el propio dispositivo con MediaPipe `LlmInference`. No necesita internet.

## Cómo abrirla

1. Abre esta carpeta en Android Studio (Ladybug 2024.2 o más reciente) y deja que sincronice Gradle.
2. Necesitas un teléfono real con al menos 6 GB de RAM. El emulador no sirve bien para inferencia LLM.

## El modelo (paso obligatorio)

El modelo pesa unos 500 MB o más, así que no va dentro del APK. Recomiendo **Gemma 3 1B IT int4** (`gemma3-1b-it-int4.task`), que se descarga de Hugging Face en `litert-community/Gemma3-1B-IT` (hay que aceptar la licencia de Gemma).

Descárgalo en el teléfono (por ejemplo, en la carpeta Descargas). La primera vez que abras la app verás la pantalla «Elige el modelo de IA»: pulsa «Elegir archivo del modelo» y selecciona el `.task`. La app lo copia una sola vez a su almacenamiento interno (después puedes borrar el original) y lo recuerda. Para cambiarlo más tarde, pulsa «Cambiar el modelo de IA» en la tarjeta para agregar ingredientes.

Para desarrollo también sigue funcionando copiarlo con adb:

```bash
adb shell mkdir -p /data/local/tmp/llm
adb push gemma3-1b-it-int4.task /data/local/tmp/llm/model.task
```

## Cómo funciona

- **`MainActivity.kt`**: la navegación entre pantallas (inicio, generando, receta, modo cocina, «¡Buen provecho!» y favoritas), los avisos con «Deshacer» y compartir o guardar el PDF.
- **`ChefViewModel.kt`**: todo el estado en un `StateFlow`: despensa, carga del modelo, generación en streaming, porciones, favoritas, temporizador de cada paso y las preguntas a Chefcito. Cada respuesta usa una `LlmInferenceSession` nueva y un mutex evita dos inferencias a la vez.
- **`Prompts.kt`** y **`Recipe.kt`**: el prompt pide un formato fijo (nombre, tiempo, dificultad, porciones, calorías, tip, ingredientes con cantidad y pasos con minutos) y el parser lo convierte en una receta estructurada. Es tolerante: si el modelo se sale del formato, rescata lo que puede. También recalcula las cantidades al cambiar las porciones («1 taza» → «1½ tazas»).
- **`HomeScreen.kt`**, **`GeneratingScreen.kt`**, **`RecipeScreen.kt`**, **`CookingScreen.kt`**, **`DoneScreen.kt`**: las pantallas, con los componentes de **`Components.kt`** y el tema de **`Theme.kt`**.
- **`HandsFree.kt`**: el modo manos libres (lee los pasos en voz alta y escucha órdenes).
- **`RecipePdf.kt`**: el PDF de la receta con el mismo estilo de la app.

## Funciones

- Diseño «Warm Bento»: tarjetas cálidas, tipografía Nunito, la mascota Chefcito y animaciones suaves.
- Despensa guardada en el teléfono. La fecha de caducidad es opcional; los que la tienen se ordenan primero. Los urgentes (3 días o menos) y los caducados se marcan con colores, y hay un botón para quitar los caducados.
- Preferencias para la receta: rápida, vegetariana o sin gluten.
- Mientras se genera, se ve qué está haciendo Chefcito (elegir el plato, calcular ingredientes, escribir los pasos) y el texto que va saliendo.
- Vista de receta: porciones ajustables (las cantidades se recalculan), tiempo, dificultad, calorías por porción, el «tip pro» de Chefcito y la lista de ingredientes con casillas; los que ya tienes en la despensa vienen marcados como «Detectado en tu despensa».
- Favoritas: guarda una receta con el corazón y vuelve a abrirla sin generarla otra vez.
- Modo guiado: un paso por pantalla con barra de progreso, temporizador con alarma y la pantalla siempre encendida.
- Manos libres (opcional, pide permiso de micrófono): lee cada paso y entiende «siguiente», «anterior», «repite», «pausa», «inicia», «por qué» y «sustituir».
- «Pregúntale a Chefcito»: en cada paso puedes preguntar «¿Por qué hacemos esto?» o pedir un sustituto de un ingrediente.
- Al terminar, «¡Buen provecho!» con la opción de quitar de la despensa lo que usaste.
- Compartir o guardar la receta en PDF.
- Icono adaptable y pantalla de inicio animada (sartén con brote).

## Decisiones tomadas

- `tasks-genai:0.10.24`, AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, minSdk 26 (para el icono adaptable), compileSdk 35.
- Backend CPU por defecto, que funciona en todos los teléfonos. El manifest declara las librerías OpenCL opcionales por si luego quieres probar GPU.
- Los ingredientes se guardan en el teléfono (SharedPreferences) con su fecha de caducidad, así que la cuenta de días baja sola cada día.

Nota: Google está migrando la inferencia LLM de MediaPipe hacia LiteRT-LM. Esta versión de MediaPipe sigue funcionando; si Android Studio te ofrece subir `tasks-genai`, revisa sus notas de versión antes.

## Créditos

- Tipografía [Nunito](https://github.com/googlefonts/nunito), licencia SIL Open Font License 1.1 (`licenses/OFL-Nunito.txt`).
- Iconos de [Lucide](https://lucide.dev), licencia ISC (`licenses/ISC-Lucide.txt`).
