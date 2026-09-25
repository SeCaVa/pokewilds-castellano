#!/usr/bin/env python3
"""Construye la traducción al castellano de PokeWilds y la instala en una copia del juego.

Uso:
    python construir.py RUTA_DEL_JUEGO            # parchea RUTA/app/pokewilds.jar (guarda copia .original)
    python construir.py RUTA_DEL_JUEGO --probar   # además traduce castellano/pruebas.txt y muestra el resultado
    python construir.py --solo-validar            # solo comprueba es.txt

Necesita un JDK 8+ (javac) y Python 3 con Pillow (solo para regenerar la fuente).
"""
import glob
import os
import shutil
import subprocess
import sys
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
BUILD = os.path.join(HERE, 'build')
LIB = os.path.join(HERE, 'patcher', 'lib')
ASM_VERSION = '9.7.1'
ASM_JARS = ['asm', 'asm-tree']

# Caracteres que la fuente del juego sabe dibujar (los originales + los que añade Es.GLYPHS).
ALLOWED = set('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 <>_?!.,-éÉ'
              'áíóúñüÁÍÓÚÑÜ¡¿')
# Literales que acaban en diálogos de Windows (Swing) o en la fuente TTF: admiten cualquier carácter.
FREE_CHARS_PREFIXES = ('Save your progress?', 'WARNING', 'A save file with the same name',
                       'There was an error loading', 'Problem with save!', 'Please report this issue',
                       'Picked up', 'Requires')


def find_tool(name):
    exe = name + ('.exe' if os.name == 'nt' else '')
    candidates = []
    if os.environ.get('JAVA_HOME'):
        candidates.append(os.path.join(os.environ['JAVA_HOME'], 'bin', exe))
    found = shutil.which(name)
    if found:
        candidates.append(found)
    candidates += glob.glob(os.path.join(os.path.expanduser('~'), 'scoop', 'apps', '*jdk*', 'current', 'bin', exe))
    candidates += glob.glob(os.path.join('C:\\', 'Program Files', '*', 'jdk*', 'bin', exe))
    for c in candidates:
        if os.path.isfile(c):
            # javac debe existir junto a java (un JRE no sirve)
            if os.path.isfile(os.path.join(os.path.dirname(c), 'javac' + ('.exe' if os.name == 'nt' else ''))):
                return c
    sys.exit('No encuentro un JDK (%s). Instala uno (p.ej. "scoop install temurin21-jdk") o define JAVA_HOME.' % name)


def ensure_asm():
    os.makedirs(LIB, exist_ok=True)
    paths = []
    for a in ASM_JARS:
        p = os.path.join(LIB, '%s-%s.jar' % (a, ASM_VERSION))
        if not os.path.isfile(p):
            url = 'https://repo1.maven.org/maven2/org/ow2/asm/%s/%s/%s-%s.jar' % (a, ASM_VERSION, a, ASM_VERSION)
            print('Descargando', url)
            urllib.request.urlretrieve(url, p)
        paths.append(p)
    return paths


def build_es_txt():
    parts = sorted(glob.glob(os.path.join(HERE, 'datos', '*.txt')))
    out = []
    for p in parts:
        with open(p, encoding='utf-8') as f:
            out.append('# ==== %s ====\n' % os.path.basename(p))
            out.append(f.read().replace('\r\n', '\n'))
            if not out[-1].endswith('\n'):
                out.append('\n')
    return ''.join(out)


def validate(text):
    """Comprueba pares clave/traducción y caracteres dibujables."""
    errors = []
    pending = None
    kind = None
    for n, line in enumerate(text.split('\n'), 1):
        if not line or line.startswith('#'):
            continue
        k = line[0]
        body = line[2:] if len(line) > 2 else ''
        if kind == '$' or k == '$':
            body = body.replace('\\s', ' ')
        if k in '=~%$':
            if pending is not None:
                errors.append('línea %d: "%s" sin traducción (">")' % (n - 1, pending))
            pending, kind = body, k
        elif k == '>':
            if pending is None:
                errors.append('línea %d: traducción sin original' % n)
                continue
            free = kind == '$' and pending.startswith(FREE_CHARS_PREFIXES)
            if not free:
                # quitar marcadores de plantilla {..}
                check = body
                if kind in '~%':
                    import re
                    check = re.sub(r'\{[?!][0-9]+:([^}]*)\}', r'\1', check)
                    check = re.sub(r'\{[a-zA-Z]*[0-9]+\}', '', check)
                bad = sorted(set(c for c in check if c not in ALLOWED))
                if bad:
                    errors.append('línea %d: caracteres sin glyph %s en "%s"' % (n, bad, body))
            pending = None
        elif k == '@':
            if '|' not in body:
                errors.append('línea %d: nombre sin "|"' % n)
            else:
                name = body.split('|', 1)[1].strip()
                bad = sorted(set(c for c in name if c not in ALLOWED))
                if bad:
                    errors.append('línea %d: caracteres sin glyph %s en "%s"' % (n, bad, name))
        elif k == '\u2640':
            pass
        else:
            errors.append('línea %d: tipo de línea desconocido: %s' % (n, line[:40]))
    if pending is not None:
        errors.append('final: "%s" sin traducción' % pending)
    return errors


def run(cmd):
    print('>', ' '.join(cmd))
    subprocess.check_call(cmd)


def main():
    args = sys.argv[1:]
    es_txt = build_es_txt()
    errors = validate(es_txt)
    if errors:
        print('\n'.join(errors))
        sys.exit('es.txt tiene %d errores' % len(errors))
    print('es.txt OK (%d líneas)' % es_txt.count('\n'))
    if '--solo-validar' in args or not args:
        return

    game_dir = os.path.abspath(args[0])
    jar = os.path.join(game_dir, 'app', 'pokewilds.jar')
    original = jar + '.original'
    if not os.path.isfile(original):
        if not os.path.isfile(jar):
            sys.exit('No existe ' + jar)
        shutil.copy2(jar, original)
        print('Copia de seguridad:', original)

    javac = find_tool('javac')
    java = os.path.join(os.path.dirname(javac), 'java' + ('.exe' if os.name == 'nt' else ''))
    asm = ensure_asm()
    sep = ';' if os.name == 'nt' else ':'

    if os.path.isdir(BUILD):
        shutil.rmtree(BUILD)
    extra = os.path.join(BUILD, 'extra')
    classes = os.path.join(BUILD, 'patcher')
    os.makedirs(os.path.join(extra, 'castellano'))
    os.makedirs(classes)

    with open(os.path.join(extra, 'castellano', 'es.txt'), 'w', encoding='utf-8', newline='\n') as f:
        f.write(es_txt)
    for p in glob.glob(os.path.join(HERE, 'jar', 'castellano', '*')):
        shutil.copy2(p, os.path.join(extra, 'castellano'))
    # imágenes con texto (necesita Pillow)
    try:
        import imagenes  # noqa: F401
        run([sys.executable, os.path.join(HERE, 'imagenes.py'), original, extra])
    except ImportError:
        print('AVISO: sin Pillow no se traducen las imágenes (pip install pillow)')

    run([javac, '-encoding', 'UTF-8', '--release', '8', '-nowarn', '-cp', original, '-d', extra,
         os.path.join(HERE, 'src', 'com', 'pkmngen', 'game', 'Es.java')])
    run([javac, '-encoding', 'UTF-8', '-cp', sep.join(asm), '-d', classes,
         os.path.join(HERE, 'patcher', 'EsPatcher.java'), os.path.join(HERE, 'patcher', 'EsPrueba.java')])
    patched = os.path.join(BUILD, 'pokewilds.jar')
    run([java, '-cp', sep.join(asm + [classes]), 'EsPatcher', original, patched, extra])

    if '--probar' in args:
        run([java, '-cp', sep.join([extra, original, classes]), 'EsPrueba',
             os.path.join(HERE, 'pruebas.txt')])

    shutil.copy2(patched, jar)
    print('\nInstalado en', jar)
    print('Para volver al inglés: copia pokewilds.jar.original encima de pokewilds.jar')


if __name__ == '__main__':
    main()
