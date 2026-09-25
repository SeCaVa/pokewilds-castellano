# PokeWilds en castellano

Traducción al castellano (España) de **PokeWilds v0.8.11** (la versión publicada).

La v0.8.11 no usa los ficheros de `i18n/` de este repositorio: todos sus textos están
escritos dentro del código (`pokewilds.jar`). Por eso la traducción se aplica como un
**parche sobre el jar**, sin tocar la lógica del juego:

- `src/com/pkmngen/game/Es.java` — capa de traducción que se añade al jar. Traduce cada
  frase justo antes de mostrarla (cajas de texto, menús, notificaciones) y añade a la
  fuente los caracteres españoles (á é í ó ú ñ ü ¡ ¿ y sus mayúsculas).
- `patcher/EsPatcher.java` — parchea el bytecode con [ASM](https://asm.ow2.io/) para
  llamar a `Es` desde `DisplayText`, los menús `Draw*` y la carga de la fuente.
  Los nombres internos (ataques, objetos, tipos) no se tocan: solo se traducen al pintarlos,
  así que las partidas guardadas y los mods siguen funcionando.
- `imagenes.py` — reescribe con la fuente del juego las imágenes que llevan texto
  (pantalla de datos, menú de combate, barras de PS, pestañas de la mochila...).
- `fuente/generar_fuente.py` — genera `jar/castellano/fuente_es*.png` (los glyphs nuevos).
- `datos/*.txt` — **las traducciones**. Es lo único que hay que editar para corregir textos.

## Instalar

Requisitos: Python 3 con Pillow (`pip install pillow`) y un JDK 8 o superior
(p.ej. `scoop install temurin21-jdk`). La primera vez se descarga ASM de Maven Central.

```bash
python castellano/construir.py "RUTA/pokewilds-v0.8.11-windows-64"
```

Hace una copia `app/pokewilds.jar.original` y deja el jar traducido en `app/pokewilds.jar`.
Para volver al inglés basta con copiar el `.original` encima.

Con `--probar` además traduce las frases de ejemplo de `pruebas.txt` y muestra el resultado.
Con `--solo-validar` solo comprueba `datos/*.txt`.

## Editar traducciones (`datos/*.txt`)

| Línea | Significado |
|---|---|
| `= texto` / `> traducción` | Frase fija (los espacios al principio/final se conservan solos). |
| `~ regex` / `> plantilla` | Frase con partes variables (nombre del POKéMON, ataque...). |
| `% regex` / `> plantilla` | Igual, para textos de menú. |
| `@ nombre interno \| nombre` | Ataques, objetos, tipos... (máx. 12 caracteres; 10 en el submenú del POKéMON). |
| `$ literal` / `> nuevo` | Reemplazo directo en el bytecode (textos que el juego manipula antes de mostrarlos). `\s` = espacio. |
| `♀ NOMBRE` | Nombre femenino, para que `{El2}` ponga "La". |

En las plantillas: `{2}` grupo tal cual, `{n2}` traducido como nombre, `{El2}`/`{el2}` con
artículo, `{x2}` cantidad (" x3"), `{t2}` fragmento traducido como frase, `{?1:texto}` texto
solo si el grupo 1 existe (p.ej. el prefijo "Enemy ").

Caracteres que la fuente sabe dibujar: letras, números, espacio, `< > _ ? ! . , -` y
`á é í ó ú ñ ü Á É Í Ó Ú Ñ Ü ¡ ¿`. `construir.py` avisa si se usa cualquier otro
(`:`, `/`, `'`... no existen en la fuente; `'` se ve como "'s").

Si el juego muestra alguna frase en inglés, aparecerá en `castellano_sin_traducir.txt`
dentro de la carpeta del juego: basta con añadirla a `datos/` y volver a construir.
También se puede poner un `es.txt` corregido en `mods/castellano/es.txt` sin reconstruir
(salvo las entradas `$`, que sí necesitan reconstruir).

## Fuentes de los textos

Nombres oficiales de movimientos y objetos: PokeAPI (idioma `es`), abreviados a 12
caracteres al estilo de los juegos de GBA en castellano. Terminología de combate según
Oro/Plata/Cristal en castellano.

## Créditos

- **PokeWilds**: [SheerSt](https://github.com/SheerSt/pokewilds) y colaboradores. Este fork solo
  añade la traducción; el parche necesita el `pokewilds.jar` del release oficial y no incluye
  ni redistribuye el juego.
- Las letras españolas de la fuente (á, é, í, ó, ú, ñ, ü, ¡, ¿, É, Ü) salen de
  `text_sheet1_transparent.png` del propio repositorio original; Á, Í, Ó, Ú y Ñ están
  dibujadas siguiendo el mismo estilo.
- Nombres oficiales en castellano de movimientos y objetos: [PokeAPI](https://pokeapi.co/).
- Modificación del bytecode: [ASM](https://asm.ow2.io/) (licencia BSD). Se descarga al
  construir; no se incluye en el repositorio.
- Pokémon y sus nombres son marcas de Nintendo, Creatures Inc. y GAME FREAK inc. Proyecto
  de fans sin ánimo de lucro.
