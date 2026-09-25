"""Genera las imágenes del juego con texto en castellano.

Lee las imágenes originales de pokewilds.jar, borra el texto en inglés y lo reescribe con
la fuente del propio juego (text_sheet1.png + castellano/fuente_es.png). El resultado se
guarda con la misma ruta dentro de SALIDA para que EsPatcher lo meta en el jar.

Uso: python imagenes.py pokewilds.jar.original SALIDA
"""
import io
import os
import sys
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))

# ---------------------------------------------------------------- fuente 8x8 del juego

UPPER = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'
ES_GLYPHS = 'áíóúñüÁÍÓÚÑÜ¡¿éÉ'  # igual que Es.GLYPHS


def bitmap(im, x, y, w=8, h=8):
    rows = []
    for r in range(h):
        rows.append(''.join('#' if im.getpixel((x + c, y + r))[3] > 0 and sum(im.getpixel((x + c, y + r))[:3]) < 200
                            else '.' for c in range(w)))
    return rows


def load_font(jar):
    sheet = Image.open(io.BytesIO(jar.read('text_sheet1.png'))).convert('RGBA')
    font = {}
    for i, ch in enumerate(UPPER):
        font[ch] = bitmap(sheet, 10 + 16 * i, 5)
        font[ch.lower()] = bitmap(sheet, 10 + 16 * i, 17)
    for i in range(10):
        font[str(i)] = bitmap(sheet, 10 + 16 * i, 29)
    for ch, x in [('?', 58), ('!', 74), ('.', 122), (',', 138), ('-', 170)]:
        font[ch] = bitmap(sheet, x, 41)
    font[' '] = ['........'] * 8
    es = Image.open(os.path.join(HERE, 'jar', 'castellano', 'fuente_es.png')).convert('RGBA')
    for i, ch in enumerate(ES_GLYPHS):
        font[ch] = bitmap(es, 8 * i, 0)
    return font


# "HUIR" condensado (5 px por letra) para la columna derecha del menú de combate, que solo
# tiene 3 casillas.
CONDENSED = {
    'H': ['#...#', '#...#', '#...#', '#####', '#...#', '#...#', '#...#'],
    'U': ['#...#', '#...#', '#...#', '#...#', '#...#', '#...#', '.###.'],
    'I': ['###', '.#.', '.#.', '.#.', '.#.', '.#.', '###'],
    'R': ['####.', '#...#', '#...#', '####.', '#.#..', '#..#.', '#...#'],
}

# Fuente pequeña (6 px de alto) de la mochila: letras sacadas de las imágenes originales,
# más las que faltaban dibujadas en el mismo estilo.
SMALL = {
    'I': ['###', '.#.', '.#.', '.#.', '.#.', '###'],
    't': ['...', '.#.', '###', '.#.', '.#.', '.##'],
    'e': ['....', '....', '.##.', '####', '#...', '.###'],
    'm': ['.....', '.....', '####.', '#.#.#', '#.#.#', '#.#.#'],
    's': ['....', '....', '.###', '##..', '..##', '###.'],
    'B': ['###.', '#..#', '###.', '#..#', '#..#', '####'],
    'a': ['...', '...', '##.', '..#', '###', '###'],
    'l': ['#', '#', '#', '#', '#', '#'],
    'M': ['####.', '#.#.#', '#.#.#', '#.#.#', '#.#.#', '#.#.#'],
    'r': ['...', '...', '#.#', '##.', '#..', '#..'],
    'i': ['#', '.', '#', '#', '#', '#'],
    'd': ['..#', '..#', '.##', '#.#', '#.#', '###'],
    'c': ['...', '...', '.##', '#..', '#..', '###'],
    'n': ['...', '...', '##.', '#.#', '#.#', '#.#'],
    'O': ['.##.', '#..#', '#..#', '#..#', '#..#', '.##.'],
    'b': ['#..', '#..', '##.', '#.#', '#.#', '###'],
    'j': ['.#', '..', '.#', '.#', '.#', '#.'],
    'o': ['...', '...', '###', '#.#', '#.#', '###'],
    'C': ['.##', '#..', '#..', '#..', '#..', '.##'],
    'v': ['...', '...', '#.#', '#.#', '#.#', '.#.'],
}
SMALL_UPPER = {
    'P': ['###.', '#..#', '#..#', '###.', '#...', '#...'],
    'O': ['.##.', '#..#', '#..#', '#..#', '#..#', '.##.'],
    'C': ['.##.', '#..#', '#...', '#...', '#..#', '.##.'],
    'K': ['#..#', '#.#.', '##..', '##..', '#.#.', '#..#'],
    'E': ['###', '#..', '##.', '#..', '#..', '###'],
    'T': ['###', '.#.', '.#.', '.#.', '.#.', '.#.'],
    'I': ['###', '.#.', '.#.', '.#.', '.#.', '###'],
    'M': ['#...#', '##.##', '#.#.#', '#...#', '#...#', '#...#'],
    'S': ['###', '#..', '##.', '.##', '..#', '###'],
    'B': ['###.', '#..#', '###.', '#..#', '#..#', '###.'],
    'L': ['#..', '#..', '#..', '#..', '#..', '###'],
    'J': ['..#', '..#', '..#', '..#', '#.#', '.#.'],
}


def stamp(im, x, y, rows, color):
    for r, line in enumerate(rows):
        for c, ch in enumerate(line):
            if ch == '#' and 0 <= x + c < im.width and 0 <= y + r < im.height:
                im.putpixel((x + c, y + r), color)


def fill(im, x0, y0, x1, y1, color):
    for y in range(y0, y1):
        for x in range(x0, x1):
            im.putpixel((x, y), color)


def cell_background(im, x, y):
    """Color de fondo de una celda de 8x8: el más frecuente que no sea el de la letra."""
    counts = {}
    for r in range(8):
        for c in range(8):
            p = im.getpixel((x + c, y + r))
            if p[3] > 0 and sum(p[:3]) < 200:
                continue
            counts[p] = counts.get(p, 0) + 1
    return max(counts, key=counts.get) if counts else (255, 255, 255, 255)


def write(im, font, col, y, text, clear=0, color=(0, 0, 0, 255)):
    """Borra `clear` celdas desde la columna `col` y escribe `text` con la fuente 8x8."""
    n = max(clear, len(text))
    bgs = [cell_background(im, 8 * (col + i), y) for i in range(n)]
    for i in range(n):
        fill(im, 8 * (col + i), y, 8 * (col + i) + 8, y + 8, bgs[i])
    for i, ch in enumerate(text):
        stamp(im, 8 * (col + i), y, font[ch], color)


def write_small(im, x, y, text, glyphs, color):
    for ch in text:
        if ch == ' ':
            x += 3
            continue
        g = glyphs[ch]
        stamp(im, x, y, g, color)
        x += len(g[0]) + 1
    return x


def small_width(text, glyphs):
    return sum(3 if ch == ' ' else len(glyphs[ch][0]) + 1 for ch in text) - 1


# ---------------------------------------------------------------- "HP:" -> "PS:"

H_PAT = ['##.#', '##.#', '####', '##.#']
P_PAT = ['####', '##.#', '####', '##..']
S_PAT = ['####', '##..', '..##', '####']


def replace_hp(im):
    """Busca el rótulo "HP" de las barras de vida (4 px de alto) y lo cambia por "PS"."""
    found = 0
    w, h = im.size
    for y in range(h - 4):
        for x in range(w - 9):
            fg = im.getpixel((x, y))
            if fg[3] == 0:
                continue
            ok = True
            for pat, ox in ((H_PAT, 0), (P_PAT, 5)):
                for r in range(4):
                    for c in range(4):
                        p = im.getpixel((x + ox + c, y + r))
                        if (pat[r][c] == '#') != (p == fg):
                            ok = False
                            break
                    if not ok:
                        break
                if not ok:
                    break
            if not ok:
                continue
            bg = im.getpixel((x + 2, y))
            fill(im, x, y, x + 9, y + 4, bg)
            stamp(im, x, y, P_PAT, fg)
            stamp(im, x + 5, y, S_PAT, fg)
            found += 1
    return found


# ---------------------------------------------------------------- imágenes

def translate(jar, out_dir):
    font = load_font(jar)
    done = []

    def load(path):
        return Image.open(io.BytesIO(jar.read(path))).convert('RGBA')

    def save(im, path):
        dest = os.path.join(out_dir, path)
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        im.save(dest)
        done.append(path)

    # Pantalla de datos 1: PS / EXP / estado / tipo
    im = load('menu/stats_screen1.png')
    write(im, font, 10, 72, 'PUNTOS EXP', 10)
    write(im, font, 0, 96, 'ESTADO', 6)
    write(im, font, 10, 96, 'FALTAN', 8)
    write(im, font, 12, 112, 'PARA', 4)
    write(im, font, 0, 112, 'TIPO', 4)
    replace_hp(im)
    save(im, 'menu/stats_screen1.png')

    # Pantalla de datos 2: objeto / ataques
    im = load('menu/stats_screen2.png')
    write(im, font, 0, 64, 'OBJ.', 4)
    write(im, font, 0, 80, 'ATAQUES', 4)
    save(im, 'menu/stats_screen2.png')

    # Pantalla de datos 3: EO / características
    im = load('menu/stats_screen3.png')
    write(im, font, 0, 96, 'EO', 2)
    write(im, font, 11, 64, 'ATAQUE', 6)
    write(im, font, 11, 80, 'DEFENSA', 7)
    write(im, font, 11, 96, 'ATQ.ESP.', 8)
    write(im, font, 11, 112, 'DEF.ESP.', 8)
    write(im, font, 11, 128, 'VELOCIDAD', 5)
    save(im, 'menu/stats_screen3.png')

    # Menú de combate: LUCHA / PKMN / BOLSA / HUIR
    im = load('battle/battle_menu1.png')
    write(im, font, 10, 112, 'LUCHA', 5)
    write(im, font, 10, 128, 'BOLSA', 4)
    bg = cell_background(im, 16 * 8, 128)
    fill(im, 16 * 8, 128, 19 * 8, 136, bg)
    x = 129
    for ch in 'HUIR':
        stamp(im, x, 128, CONDENSED[ch], (0, 0, 0, 255))
        x += len(CONDENSED[ch][0]) + 1
    save(im, 'battle/battle_menu1.png')

    # Zona Safari
    im = load('battle/battle_text_safarizone.png')
    write(im, font, 14, 112, 'CEBO', 4)
    write(im, font, 2, 128, 'TIRAR ROCA', 10)
    write(im, font, 14, 128, 'HUIR', 3)
    save(im, 'battle/battle_text_safarizone.png')

    # Recuadro del tipo en el menú de ataques ("TYPE/" no está alineado a la cuadrícula)
    im = load('menu/attack_screen1.png')
    if not relabel(im, font, 'TYPE', 'TIPO'):
        print('AVISO: no encontré TYPE/ en attack_screen1.png')
    save(im, 'menu/attack_screen1.png')

    # Mochila: cabecera y pestañas en fuente pequeña
    im = load('menu/item_menu_gsc1.png')
    fill(im, 17, 1, 60, 7, (0, 0, 0, 255))
    write_small(im, 19, 1, 'BOLSILLO', SMALL_UPPER, (255, 255, 255, 255))
    fill(im, 100, 1, 135, 7, (0, 0, 0, 255))
    write_small(im, 103, 1, 'OBJETOS', SMALL_UPPER, (248, 88, 248, 255))
    save(im, 'menu/item_menu_gsc1.png')

    im = load('menu/bag-sheet1.png')
    for t, label in enumerate(['Objetos', 'Balls', 'Material', 'Clave', 'Medicina']):
        x0 = 41 * t + 2
        fill(im, x0, 8, x0 + 36, 14, (0, 0, 0, 255))
        wdt = small_width(label, SMALL)
        write_small(im, x0 + (36 - wdt) // 2, 8, label, SMALL, (255, 255, 255, 255))
    save(im, 'menu/bag-sheet1.png')

    # Barras de vida: HP -> PS
    for path in ['menu/gsc/health_bar1.png', 'menu/health_bar.png',
                 'battle/gsc/friendly_healthbar1.png', 'battle/gsc/friendly_healthbar1_inverse.png',
                 'battle/gsc/enemy_healthbar1.png', 'battle/gsc/enemy_healthbar1_inverse.png',
                 'battle/friendly_healthbar1.png', 'battle/friendly_healthbar1_inverse.png',
                 'battle/enemy_healthbar1.png', 'battle/enemy_healthbar1_inverse.png']:
        if path not in jar.namelist():
            continue
        im = load(path)
        n = replace_hp(im)
        if n:
            save(im, path)
        else:
            print('AVISO: sin rótulo HP en', path)
    return done


def relabel(im, font, old, new):
    """Busca `old` escrito con la fuente 8x8 en cualquier posición y lo sustituye."""
    target = [font[ch] for ch in old]
    w, h = im.size
    for y in range(h - 8):
        for x in range(w - 8 * len(old)):
            if all(bitmap(im, x + 8 * i, y) == target[i] for i in range(len(old))):
                bg = cell_background(im, x, y)
                fill(im, x, y, x + 8 * len(old), y + 8, bg)
                for i, ch in enumerate(new):
                    stamp(im, x + 8 * i, y, font[ch], (0, 0, 0, 255))
                return True
    return False


def main():
    jar = zipfile.ZipFile(sys.argv[1])
    done = translate(jar, sys.argv[2])
    print('Imágenes traducidas:', len(done))


if __name__ == '__main__':
    main()
