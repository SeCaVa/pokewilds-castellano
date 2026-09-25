# PokeWilds en castellano: referencia técnica

La descripción general, la instalación y los créditos están en el [README principal](../README.md).
Aquí está el detalle para quien quiera corregir o ampliar la traducción.

## Estructura

- `src/com/pkmngen/game/Es.java`: capa de traducción que se añade al jar. Traduce cada frase justo
  antes de mostrarla (cajas de texto, menús, notificaciones) y añade a la fuente los caracteres
  españoles.
- `patcher/EsPatcher.java`: parchea el bytecode con [ASM](https://asm.ow2.io/) para llamar a `Es` desde
  `DisplayText`, los menús `Draw*` y la carga de la fuente. Los nombres internos (ataques, objetos,
  tipos) no se tocan: solo se traducen al pintarlos.
- `imagenes.py`: reescribe con la fuente del juego las imágenes que llevan texto.
- `fuente/generar_fuente.py`: genera `jar/castellano/fuente_es*.png` (las letras nuevas).
- `datos/*.txt`: las traducciones.
- `construir.py`: valida, compila, parchea e instala. Con `--probar` traduce además las frases de
  ejemplo de `pruebas.txt`; con `--solo-validar` solo comprueba `datos/*.txt`.

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
