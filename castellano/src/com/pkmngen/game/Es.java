package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.pkmngen.game.util.SpriteProxy;
import com.pkmngen.game.util.TextureCache;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Capa de traducción al castellano para PokeWilds.
 *
 * El parche (EsPatcher) inyecta llamadas a esta clase en:
 *  - los constructores de DisplayText / DisplayTextIntro  -> tr(String)
 *  - los String.toCharArray()/charAt() de los menús Draw* -> chars(String) / ui(String)
 *  - la inicialización de las fuentes de Game              -> font(Map, int)
 *
 * Los textos se leen de /castellano/es.txt dentro del jar (o de mods/castellano/es.txt
 * si existe, para poder corregir traducciones sin volver a parchear).
 */
public class Es {

    /** Glyphs añadidos, en el mismo orden que en castellano/fuente_es*.png (celdas de 8x8). */
    static final String GLYPHS = "áíóúñüÁÍÓÚÑÜ¡¿éÉ";

    static final Map<String, String> exact = new HashMap<String, String>();
    static final Map<String, String> names = new HashMap<String, String>();
    static final Set<String> feminine = new HashSet<String>();
    static final List<Rule> rules = new ArrayList<Rule>();
    static final List<Rule> uiRules = new ArrayList<Rule>();
    static final Map<String, String> uiCache = new ConcurrentHashMap<String, String>();
    static final Set<String> logged = new HashSet<String>();
    static boolean loaded = false;
    static boolean logMissing = true;

    static final class Rule {
        final Pattern pattern;
        final String template;

        Rule(String regex, String template) {
            this.pattern = Pattern.compile(regex);
            this.template = template;
        }
    }

    // ------------------------------------------------------------------ carga

    static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            InputStream in = null;
            File override = new File("mods/castellano/es.txt");
            if (override.isFile()) {
                in = new java.io.FileInputStream(override);
            }
            if (in == null) {
                in = Es.class.getResourceAsStream("/castellano/es.txt");
            }
            if (in == null) {
                System.err.println("[castellano] no se encontró es.txt");
                return;
            }
            parse(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
            in.close();
            System.out.println("[castellano] cargado: " + exact.size() + " frases, " + names.size()
                    + " nombres, " + rules.size() + " reglas");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Formato de es.txt:
     *   = texto original exacto          (frase completa, sensible a mayúsculas)
     *   > traducción
     *   ~ expresión regular (Java)       (frase dinámica mostrada en caja de texto)
     *   > plantilla
     *   % expresión regular (Java)       (texto de menú dinámico)
     *   > plantilla
     *   @ nombre en inglés | nombre      (ataques, objetos, tipos, estadísticas...)
     *   ♀ NOMBRE TRADUCIDO               (nombres femeninos, para el artículo "la")
     *   $ literal del código             (lo reemplaza EsPatcher dentro de los .class)
     *   # comentario
     */
    static void parse(BufferedReader br) throws java.io.IOException {
        String line;
        String pendingExact = null;
        String pendingRegex = null;
        boolean pendingUi = false;
        int n = 0;
        while ((line = br.readLine()) != null) {
            n++;
            if (line.startsWith("﻿")) {
                line = line.substring(1);
            }
            if (line.length() < 2 || line.startsWith("#")) {
                continue;
            }
            char kind = line.charAt(0);
            String body = line.length() > 2 ? line.substring(2) : "";
            switch (kind) {
                case '=':
                    pendingExact = body;
                    pendingRegex = null;
                    break;
                case '~':
                case '%':
                    pendingRegex = body;
                    pendingUi = kind == '%';
                    pendingExact = null;
                    break;
                case '>':
                    if (pendingExact != null) {
                        exact.put(pendingExact, body);
                        pendingExact = null;
                    } else if (pendingRegex != null) {
                        try {
                            (pendingUi ? uiRules : rules).add(new Rule(pendingRegex, body));
                        } catch (Exception e) {
                            System.err.println("[castellano] regex mala en línea " + n + ": " + e);
                        }
                        pendingRegex = null;
                    }
                    break;
                case '@': {
                    int bar = body.indexOf('|');
                    if (bar > 0) {
                        names.put(body.substring(0, bar).trim().toLowerCase(Locale.ROOT), body.substring(bar + 1).trim());
                    }
                    break;
                }
                case '$':
                    // literal reemplazado en el bytecode por EsPatcher; aquí se ignora
                    pendingExact = null;
                    pendingRegex = null;
                    break;
                case '♀':
                    feminine.add(body.trim().toUpperCase(Locale.ROOT));
                    break;
                default:
                    break;
            }
        }
    }

    /** Busca una frase exacta; si no está, prueba sin espacios sobrantes y los conserva. */
    static String exactLookup(String s) {
        String r = exact.get(s);
        if (r != null) {
            return r;
        }
        String t = s.trim();
        if (t.length() == s.length() || t.isEmpty()) {
            return null;
        }
        r = exact.get(t);
        if (r == null) {
            return null;
        }
        int lead = s.indexOf(t);
        return s.substring(0, lead) + r + s.substring(lead + t.length());
    }

    // ------------------------------------------------------------ traducción

    /** Traduce una frase completa que se va a mostrar en una caja de texto. */
    public static String tr(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        load();
        String r = exactLookup(s);
        if (r != null) {
            return r;
        }
        for (Rule rule : rules) {
            Matcher m = rule.pattern.matcher(s);
            if (m.matches()) {
                return apply(rule.template, m);
            }
        }
        r = name(s);
        if (r != null) {
            return r;
        }
        missing(s);
        return s;
    }

    /** Traduce un texto de menú. Si no hay traducción se devuelve tal cual (sin registrar). */
    public static String ui(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        String cached = uiCache.get(s);
        if (cached != null) {
            return cached;
        }
        load();
        String r = exactLookup(s);
        if (r == null) {
            r = name(s);
        }
        if (r == null) {
            for (Rule rule : uiRules) {
                Matcher m = rule.pattern.matcher(s);
                if (m.matches()) {
                    r = apply(rule.template, m);
                    break;
                }
            }
        }
        if (r == null) {
            r = s;
        }
        if (uiCache.size() < 20000) {
            uiCache.put(s, r);
        }
        return r;
    }

    /** Submenú del POKéMON: los movimientos de campo usan nombres cortos ("campo:headbutt"). */
    public static String uiCampo(String s) {
        if (s != null) {
            load();
            String r = names.get("campo:" + s.trim().toLowerCase(Locale.ROOT));
            if (r != null) {
                return isUpper(s) ? r.toUpperCase(Locale.ROOT) : r;
            }
        }
        return ui(s);
    }

    /** Nombre corto para la lista de requisitos de construcción (clave "req:nombre"). */
    public static String req(String s, Locale locale) {
        load();
        String r = names.get("req:" + s.trim().toLowerCase(Locale.ROOT));
        if (r == null) {
            r = name(s);
        }
        return (r == null ? s : r).toUpperCase(locale);
    }

    /**
     * Palabras de un requisito de construcción, una por línea. El juego añade la cantidad
     * ("x1") a la última palabra rellenándola hasta 5 letras, y cada línea admite 7 caracteres:
     * si la última palabra es más larga, se añade una línea vacía para que la cantidad vaya
     * sola debajo ("     x1") en vez de salirse de la caja.
     */
    public static String[] reqSplit(String s, String regex) {
        String[] words = s.split(regex);
        if (words.length == 0 || words[words.length - 1].length() <= 5) {
            return words;
        }
        String[] out = new String[words.length + 1];
        System.arraycopy(words, 0, out, 0, words.length);
        out[words.length] = "";
        return out;
    }

    /** Textos compuestos que se pintan con la fuente TTF (notificaciones). */
    public static String sb(StringBuilder b) {
        return ui(b.toString());
    }

    /** Como chars(), pero los nombres de tipo ("tipo:grass") tienen prioridad (GRASS = PLANTA). */
    public static char[] charsT(String s) {
        if (s != null) {
            load();
            String r = names.get("tipo:" + s.trim().toLowerCase(Locale.ROOT));
            if (r != null) {
                return (isUpper(s) ? r.toUpperCase(Locale.ROOT) : r).toCharArray();
            }
        }
        return chars(s);
    }

    public static char[] chars(String s) {
        return ui(s).toCharArray();
    }

    /** Traduce un nombre (ataque, objeto, tipo...) respetando si venía en MAYÚSCULAS. */
    static String name(String s) {
        String key = s.trim().toLowerCase(Locale.ROOT);
        String r = names.get(key);
        if (r == null && key.endsWith("es")) {
            r = names.get(key.substring(0, key.length() - 2)); // plural inglés: "BOXES"
        }
        if (r == null && key.endsWith("s")) {
            r = names.get(key.substring(0, key.length() - 1)); // plural inglés: "LOGS"
        }
        if (r == null) {
            return null;
        }
        if (isUpper(s)) {
            r = r.toUpperCase(Locale.ROOT);
        }
        // conservar espacios alrededor del original (p.ej. "FIRE " en menús)
        int lead = 0;
        while (lead < s.length() && s.charAt(lead) == ' ') lead++;
        int trail = 0;
        while (trail < s.length() - lead && s.charAt(s.length() - 1 - trail) == ' ') trail++;
        return s.substring(0, lead) + r + s.substring(s.length() - trail);
    }

    static String nameOrSelf(String s) {
        if (s == null) {
            return "";
        }
        String r = name(s);
        return r == null ? s : r;
    }

    static boolean isUpper(String s) {
        boolean letter = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) {
                letter = true;
                if (Character.isLowerCase(c) && c != 'é') { // POKéMON
                    return false;
                }
            }
        }
        return letter;
    }

    /**
     * Plantillas:
     *   {2}      grupo 2 tal cual
     *   {n2}     grupo 2 traducido como nombre
     *   {el2}    "el/la" + nombre traducido       {El2} igual pero con mayúscula inicial
     *   {del2}   "del/de la" + nombre traducido   {al2}  "al/a la" + nombre traducido
     *   {x2}     " x" + cantidad (vacío si es 1/a/an)
     *   {t2}     grupo traducido como frase exacta
     *   {?1:txt} txt si el grupo 1 no está vacío  {!1:txt} txt si está vacío
     */
    static String apply(String template, Matcher m) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c != '{') {
                out.append(c);
                i++;
                continue;
            }
            int end = template.indexOf('}', i);
            if (end < 0) {
                out.append(template.substring(i));
                break;
            }
            String tok = template.substring(i + 1, end);
            i = end + 1;
            if (tok.length() > 1 && (tok.charAt(0) == '?' || tok.charAt(0) == '!')) {
                int colon = tok.indexOf(':');
                int g = Integer.parseInt(tok.substring(1, colon));
                String val = group(m, g);
                boolean present = val != null && !val.isEmpty();
                if (present == (tok.charAt(0) == '?')) {
                    out.append(tok.substring(colon + 1));
                }
                continue;
            }
            String prefix = tok.replaceAll("[0-9]+$", "");
            int g = Integer.parseInt(tok.substring(prefix.length()));
            String val = group(m, g);
            if (val == null) {
                val = "";
            }
            if (prefix.isEmpty()) {
                out.append(val);
            } else if (prefix.equals("x")) {
                // cantidad: " x3" (nada si es 1, "a" o "an")
                if (val.matches("[0-9]+") && !val.equals("1")) {
                    out.append(" x").append(val);
                }
            } else if (prefix.equals("n")) {
                out.append(nameOrSelf(val));
            } else if (prefix.equals("t")) {
                // fragmento que es una frase completa
                String t = exactLookup(val);
                out.append(t == null ? val : t);
            } else {
                String n = nameOrSelf(val);
                boolean fem = feminine.contains(n.toUpperCase(Locale.ROOT));
                String art;
                if (prefix.equalsIgnoreCase("el")) {
                    art = fem ? "la " : "el ";
                } else if (prefix.equals("del")) {
                    art = fem ? "de la " : "del ";
                } else if (prefix.equals("al")) {
                    art = fem ? "a la " : "al ";
                } else {
                    art = "";
                }
                if (Character.isUpperCase(prefix.charAt(0)) && !art.isEmpty()) {
                    art = Character.toUpperCase(art.charAt(0)) + art.substring(1);
                }
                out.append(art).append(n);
            }
        }
        return out.toString();
    }

    static String group(Matcher m, int g) {
        return g <= m.groupCount() ? m.group(g) : null;
    }

    static synchronized void missing(String s) {
        if (!logMissing || logged.contains(s) || logged.size() > 5000) {
            return;
        }
        boolean hasLetters = false;
        for (int i = 0; i < s.length(); i++) {
            if ("¡¿áíóúñÁÍÓÚÑ".indexOf(s.charAt(i)) >= 0) {
                return; // ya está en castellano (p.ej. un literal traducido por EsPatcher)
            }
            if (Character.isLetter(s.charAt(i))) {
                hasLetters = true;
                break;
            }
        }
        if (!hasLetters) {
            return;
        }
        logged.add(s);
        try {
            Writer w = new OutputStreamWriter(new FileOutputStream("castellano_sin_traducir.txt", true), StandardCharsets.UTF_8);
            w.write(s.replace("\n", "\\n") + "\n");
            w.close();
        } catch (Exception e) {
            // no es crítico
        }
    }

    // ------------------------------------------------------------------ fuente

    /**
     * Añade los glyphs españoles al diccionario de letras.
     * variant: 0 = text_sheet1 (fondo blanco), 1 = transparente, 2 = inverso (letras blancas).
     */
    public static void font(Map dict, int variant) {
        if (dict == null) {
            return;
        }
        try {
            String file = variant == 0 ? "castellano/fuente_es.png"
                    : variant == 1 ? "castellano/fuente_es_transparent.png"
                    : "castellano/fuente_es_inverse.png";
            Texture tex = TextureCache.get(Gdx.files.internal(file));
            for (int i = 0; i < GLYPHS.length(); i++) {
                dict.put(Character.valueOf(GLYPHS.charAt(i)), new SpriteProxy(tex, 8 * i, 0, 8, 8));
            }
            // Caracteres sin glyph propio: usar la letra base para que nunca salga un hueco.
            String from = "àâäãèêëìîïòôöõùûÀÂÄÃÈÊËÌÎÏÒÔÖÕÙÛçÇ";
            String to = "aaaaeeeiiiioooouuAAAAEEEIIIIOOOOUUcC";
            for (int i = 0; i < from.length(); i++) {
                Character key = Character.valueOf(from.charAt(i));
                if (!dict.containsKey(key)) {
                    Object base = dict.get(Character.valueOf(to.charAt(i)));
                    if (base != null) {
                        dict.put(key, base);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
