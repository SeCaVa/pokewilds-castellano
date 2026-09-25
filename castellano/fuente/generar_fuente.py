"""Genera castellano/fuente_es*.png con los glyphs españoles (celdas de 8x8).

Orden (debe coincidir con Es.GLYPHS): á í ó ú ñ ü Á Í Ó Ú Ñ Ü ¡ ¿ é É
Las minúsculas, ¡ ¿ y Ü se copian de text_sheet1_transparent.png del repo;
Á Í Ó Ú Ñ están dibujadas a mano abajo siguiendo el estilo de É.
"""
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, '..', '..'))
OUT = os.path.join(HERE, '..', 'jar', 'castellano')

sheet = Image.open(os.path.join(REPO, 'text_sheet1_transparent.png')).convert('RGBA')


def from_sheet(col, row_y):
    x = 10 + 16 * col
    rows = []
    for r in range(8):
        line = ''
        for c in range(8):
            p = sheet.getpixel((x + c, row_y + r))
            line += '#' if p[3] > 0 and p[0] < 128 else '.'
        rows.append(line)
    return rows


DRAWN = {
    # Acento de 1 píxel y letra de 6 filas, para que no parezcan minúsculas.
    'Á': ['....#...',
          '..#.#...',
          '.#...#..',
          '.#...#..',
          '.#####..',
          '#.....#.',
          '#.....#.',
          '........'],
    'Í': ['....#...',
          '.#####..',
          '...#....',
          '...#....',
          '...#....',
          '...#....',
          '.#####..',
          '........'],
    'Ó': ['....#...',
          '..###...',
          '.#...#..',
          '#.....#.',
          '#.....#.',
          '.#...#..',
          '..###...',
          '........'],
    'Ú': ['....#...',
          '#.....#.',
          '#.....#.',
          '#.....#.',
          '#.....#.',
          '.#...#..',
          '..###...',
          '........'],
    'Ñ': ['...#.#..',
          '..#.#...',
          '##....#.',
          '#.#...#.',
          '#..#..#.',
          '#...#.#.',
          '#....##.',
          '........'],
}

GLYPHS = [
    ('á', lambda: from_sheet(14, 29)),
    ('í', lambda: from_sheet(21, 29)),
    ('ó', lambda: from_sheet(24, 29)),
    ('ú', lambda: from_sheet(18, 41)),
    ('ñ', lambda: from_sheet(19, 41)),
    ('ü', lambda: from_sheet(17, 41)),
    ('Á', lambda: DRAWN['Á']),
    ('Í', lambda: DRAWN['Í']),
    ('Ó', lambda: DRAWN['Ó']),
    ('Ú', lambda: DRAWN['Ú']),
    ('Ñ', lambda: DRAWN['Ñ']),
    ('Ü', lambda: from_sheet(2, 53)),
    ('¡', lambda: from_sheet(21, 41)),
    ('¿', lambda: from_sheet(22, 41)),
    # El juego ya tiene é/É, pero su glyph es el de "POKéMON" de Game Boy (parece una ê);
    # se sustituyen por un acento agudo normal.
    ('é', lambda: from_sheet(20, 41)),
    ('É', lambda: from_sheet(1, 53)),
]

# (color de letra, fondo, color auxiliar). El color auxiliar es el mismo que llevan las hojas
# originales del juego: SpriteProxy lo busca al analizar la textura y falla si no lo encuentra.
VARIANTS = {
    'fuente_es.png': ((0, 0, 0, 255), (255, 255, 255, 255), (255, 251, 255, 255)),
    'fuente_es_transparent.png': ((0, 0, 0, 255), (255, 255, 255, 0), (255, 251, 255, 0)),
    'fuente_es_inverse.png': ((255, 255, 255, 255), (0, 0, 0, 0), (0, 4, 0, 0)),
}


def main():
    os.makedirs(OUT, exist_ok=True)
    bitmaps = [(ch, fn()) for ch, fn in GLYPHS]
    for fname, (fg, bg, helper) in VARIANTS.items():
        # una celda extra al final solo con el color auxiliar
        im = Image.new('RGBA', (8 * (len(bitmaps) + 1), 8), bg)
        for y in range(8):
            for x in range(8):
                im.putpixel((8 * len(bitmaps) + x, y), helper)
        for i, (ch, rows) in enumerate(bitmaps):
            for y, line in enumerate(rows):
                for x, c in enumerate(line):
                    if c == '#':
                        im.putpixel((8 * i + x, y), fg)
        im.save(os.path.join(OUT, fname))
    # vista previa ampliada
    src = Image.open(os.path.join(OUT, 'fuente_es.png')).convert('RGB')
    prev = src.resize((src.width * 6, 48), Image.NEAREST)
    prev.save(os.path.join(HERE, 'vista_previa.png'))
    print('ok', ''.join(ch for ch, _ in bitmaps))


if __name__ == '__main__':
    main()
