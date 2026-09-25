import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Parchea pokewilds.jar para enganchar la capa de traducción com.pkmngen.game.Es.
 *
 * Uso: java -cp asm.jar;asm-tree.jar;. EsPatcher original.jar salida.jar extra_dir
 *   extra_dir contiene los ficheros a añadir al jar (Es.class, castellano/es.txt, fuentes...).
 */
public class EsPatcher {

    static final String ES = "com/pkmngen/game/Es";
    static final String PKG = "com/pkmngen/game/";
    static final Map<String, Integer> stats = new LinkedHashMap<String, Integer>();
    /** Literales reemplazados directamente en el bytecode (entradas "$" de es.txt). */
    static final Map<String, String> literals = new LinkedHashMap<String, String>();
    static final Set<String> literalsUsed = new HashSet<String>();

    public static void main(String[] args) throws Exception {
        File in = new File(args[0]);
        File out = new File(args[1]);
        File extra = new File(args[2]);

        Map<String, byte[]> extras = new LinkedHashMap<String, byte[]>();
        collect(extra, "", extras);
        loadLiterals(extras.get("castellano/es.txt"));

        ZipFile zin = new ZipFile(in);
        ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(out));
        Set<String> written = new HashSet<String>();
        Enumeration<? extends ZipEntry> en = zin.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            String name = e.getName();
            if (written.contains(name)) {
                continue;
            }
            byte[] data;
            if (extras.containsKey(name)) {
                data = extras.remove(name);
            } else {
                data = readAll(zin.getInputStream(e));
                if (name.startsWith(PKG) && name.endsWith(".class")) {
                    data = transform(name.substring(0, name.length() - 6), data);
                }
            }
            // Las firmas del jar original ya no serían válidas.
            if (name.startsWith("META-INF/") && (name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA"))) {
                continue;
            }
            ZipEntry ne = new ZipEntry(name);
            ne.setTime(e.getTime());
            zout.putNextEntry(ne);
            zout.write(data);
            zout.closeEntry();
            written.add(name);
        }
        for (Map.Entry<String, byte[]> x : extras.entrySet()) {
            zout.putNextEntry(new ZipEntry(x.getKey()));
            zout.write(x.getValue());
            zout.closeEntry();
        }
        zout.close();
        zin.close();
        for (Map.Entry<String, Integer> s : stats.entrySet()) {
            System.out.println(s.getKey() + ": " + s.getValue());
        }
        for (String k : literals.keySet()) {
            if (!literalsUsed.contains(k)) {
                System.out.println("AVISO literal no encontrado en el jar: " + k);
            }
        }
    }

    static void loadLiterals(byte[] esTxt) throws Exception {
        if (esTxt == null) {
            throw new IllegalStateException("falta castellano/es.txt");
        }
        String[] lines = new String(esTxt, "UTF-8").split("\r?\n");
        String pending = null;
        for (String line : lines) {
            if (line.startsWith("﻿")) {
                line = line.substring(1);
            }
            // "\s" = espacio (para literales con espacios al final, que los editores suelen borrar)
            line = line.replace("\\s", " ");
            if (line.startsWith("$ ")) {
                pending = line.substring(2);
            } else if (line.startsWith("> ") || line.equals(">")) {
                if (pending != null) {
                    literals.put(pending, line.length() > 2 ? line.substring(2) : "");
                }
                pending = null;
            } else if (line.length() > 0 && "=~%@".indexOf(line.charAt(0)) >= 0) {
                pending = null;
            }
        }
    }

    static void count(String what) {
        Integer v = stats.get(what);
        stats.put(what, v == null ? 1 : v + 1);
    }

    static void collect(File dir, String prefix, Map<String, byte[]> out) throws Exception {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                collect(f, prefix + f.getName() + "/", out);
            } else {
                out.put(prefix + f.getName(), Files.readAllBytes(f.toPath()));
            }
        }
    }

    static byte[] readAll(InputStream is) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = is.read(buf)) > 0) {
            bo.write(buf, 0, n);
        }
        is.close();
        return bo.toByteArray();
    }

    static String simpleName(String internal) {
        return internal.substring(PKG.length());
    }

    /** Clases de menús que dibujan texto letra a letra. */
    static boolean isMenuClass(String simple) {
        String outer = simple.contains("$") ? simple.substring(0, simple.indexOf('$')) : simple;
        return outer.startsWith("Draw") || outer.equals("GainExpAnimationGen2") || outer.equals("GainExpAnimation");
    }

    static byte[] transform(String internal, byte[] data) {
        String simple = simpleName(internal);
        if (simple.equals("Es")) {
            return data;
        }
        boolean displayText = simple.equals("DisplayText") || simple.equals("DisplayTextIntro");
        boolean menu = isMenuClass(simple);
        boolean game = simple.equals("Game");
        boolean notify = simple.equals("ItemPickupNotify") || simple.equals("RequirementNotify");
        ClassNode cn = new ClassNode();
        new ClassReader(data).accept(cn, 0);
        boolean changed = false;
        for (MethodNode mn : cn.methods) {
            changed |= replaceLiterals(mn);
            if (notify) {
                changed |= hookStringBuilder(mn);
            }
            if (simple.equals("DrawBuildRequirements")) {
                changed |= hookRequirementNames(mn);
            }
            if (displayText && mn.name.equals("<init>")) {
                changed |= hookConstructor(cn, mn);
            }
            if (menu) {
                boolean types = simple.startsWith("DrawStatsScreen") || simple.startsWith("DrawAttacksMenu");
                changed |= hookToCharArray(mn, types ? "charsT" : "chars");
                if (!simple.startsWith("DrawSetupMenu")) {
                    // el submenú del POKéMON muestra movimientos de campo: nombres cortos "campo:"
                    changed |= hookCharAtLocals(mn, simple.startsWith("DrawPokemonMenu$SelectedMenu") ? "uiCampo" : "ui");
                }
            }
            if (game) {
                changed |= hookFonts(mn);
            }
        }
        if (!changed) {
            return data;
        }
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    /** textString = Es.tr(textString) al principio de los constructores que llaman a super(). */
    static boolean hookConstructor(ClassNode cn, MethodNode mn) {
        boolean callsSuper = false;
        for (AbstractInsnNode n = mn.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n.getOpcode() == Opcodes.INVOKESPECIAL && ((MethodInsnNode) n).name.equals("<init>")) {
                MethodInsnNode m = (MethodInsnNode) n;
                callsSuper = !m.owner.equals(cn.name);
                break;
            }
        }
        if (!callsSuper) {
            return false;
        }
        Type[] args = Type.getArgumentTypes(mn.desc);
        int slot = 1;
        for (Type t : args) {
            if (t.getDescriptor().equals("Ljava/lang/String;")) {
                InsnList il = new InsnList();
                il.add(new VarInsnNode(Opcodes.ALOAD, slot));
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ES, "tr", "(Ljava/lang/String;)Ljava/lang/String;", false));
                il.add(new VarInsnNode(Opcodes.ASTORE, slot));
                mn.instructions.insert(il);
                count("DisplayText constructores");
                return true; // solo el primer String (el texto)
            }
            slot += t.getSize();
        }
        return false;
    }

    static boolean replaceLiterals(MethodNode mn) {
        if (literals.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (AbstractInsnNode n = mn.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n instanceof LdcInsnNode && ((LdcInsnNode) n).cst instanceof String) {
                String v = (String) ((LdcInsnNode) n).cst;
                String r = literals.get(v);
                if (r != null) {
                    ((LdcInsnNode) n).cst = r;
                    literalsUsed.add(v);
                    changed = true;
                    count("literales");
                }
            }
        }
        return changed;
    }

    /** sb.toString() -> Es.sb(sb): traduce textos que se pintan con la fuente TTF. */
    static boolean hookStringBuilder(MethodNode mn) {
        boolean changed = false;
        for (AbstractInsnNode n = mn.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                MethodInsnNode m = (MethodInsnNode) n;
                if (m.owner.equals("java/lang/StringBuilder") && m.name.equals("toString")) {
                    MethodInsnNode r = new MethodInsnNode(Opcodes.INVOKESTATIC, ES, "sb", "(Ljava/lang/StringBuilder;)Ljava/lang/String;", false);
                    mn.instructions.set(m, r);
                    n = r;
                    changed = true;
                    count("notificaciones");
                }
            }
        }
        return changed;
    }

    /** Requisitos de construcción: req.toUpperCase(locale) -> Es.req(req, locale) (nombre corto). */
    static boolean hookRequirementNames(MethodNode mn) {
        boolean changed = false;
        for (AbstractInsnNode n = mn.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                MethodInsnNode m = (MethodInsnNode) n;
                if (m.owner.equals("java/lang/String") && m.name.equals("toUpperCase") && m.desc.equals("(Ljava/util/Locale;)Ljava/lang/String;")) {
                    MethodInsnNode r = new MethodInsnNode(Opcodes.INVOKESTATIC, ES, "req", "(Ljava/lang/String;Ljava/util/Locale;)Ljava/lang/String;", false);
                    mn.instructions.set(m, r);
                    n = r;
                    changed = true;
                    count("requisitos");
                }
            }
        }
        return changed;
    }

    static boolean hookToCharArray(MethodNode mn, String target) {
        boolean changed = false;
        for (AbstractInsnNode n = mn.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                MethodInsnNode m = (MethodInsnNode) n;
                if (m.owner.equals("java/lang/String") && m.name.equals("toCharArray") && m.desc.equals("()[C")) {
                    MethodInsnNode r = new MethodInsnNode(Opcodes.INVOKESTATIC, ES, target, "(Ljava/lang/String;)[C", false);
                    mn.instructions.set(m, r);
                    n = r;
                    changed = true;
                    count("toCharArray");
                }
            }
        }
        return changed;
    }

    /**
     * Menús que dibujan con "for (j < word.length()) word.charAt(j)": se traduce el String
     * justo en esas lecturas (aload word -> Es.ui), sin tocar el valor guardado en la variable,
     * para que la lógica (p.ej. word.equals("CUT")) siga viendo el texto original.
     */
    static boolean hookCharAtLocals(MethodNode mn, String target) {
        Set<Integer> slots = new HashSet<Integer>();
        List<AbstractInsnNode> loads = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode n = mn.instructions.getFirst(); n != null; n = n.getNext()) {
            if (isStringCall(n, "charAt")) {
                AbstractInsnNode idx = prevReal(n);
                AbstractInsnNode recv = idx == null ? null : prevReal(idx);
                if (idx != null && idx.getOpcode() == Opcodes.ILOAD && recv != null && recv.getOpcode() == Opcodes.ALOAD) {
                    slots.add(((VarInsnNode) recv).var);
                    loads.add(recv);
                }
            }
        }
        if (slots.isEmpty()) {
            return false;
        }
        for (AbstractInsnNode n = mn.instructions.getFirst(); n != null; n = n.getNext()) {
            if (isStringCall(n, "length")) {
                AbstractInsnNode recv = prevReal(n);
                if (recv != null && recv.getOpcode() == Opcodes.ALOAD && slots.contains(((VarInsnNode) recv).var)) {
                    loads.add(recv);
                }
            }
        }
        for (AbstractInsnNode n : loads) {
            mn.instructions.insert(n, new MethodInsnNode(Opcodes.INVOKESTATIC, ES, target, "(Ljava/lang/String;)Ljava/lang/String;", false));
            count("charAt/length");
        }
        return true;
    }

    static boolean isStringCall(AbstractInsnNode n, String name) {
        if (n.getOpcode() != Opcodes.INVOKEVIRTUAL) {
            return false;
        }
        MethodInsnNode m = (MethodInsnNode) n;
        return m.owner.equals("java/lang/String") && m.name.equals(name);
    }

    static AbstractInsnNode prevReal(AbstractInsnNode n) {
        AbstractInsnNode p = n.getPrevious();
        while (p != null && p.getOpcode() < 0) {
            p = p.getPrevious();
        }
        return p;
    }

    static boolean hookFonts(MethodNode mn) {
        String field;
        int variant;
        if (mn.name.equals("initTextDict")) {
            field = null;
            variant = 0;
        } else if (mn.name.equals("initTransparentDict")) {
            field = "transparentDict";
            variant = 1;
        } else if (mn.name.equals("initInverseTextDict")) {
            field = "textDictInverse";
            variant = 2;
        } else {
            return false;
        }
        boolean changed = false;
        List<AbstractInsnNode> rets = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode n = mn.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n.getOpcode() == Opcodes.ARETURN || n.getOpcode() == Opcodes.RETURN) {
                rets.add(n);
            }
        }
        for (AbstractInsnNode r : rets) {
            InsnList il = new InsnList();
            if (field == null) {
                il.add(new InsnNode(Opcodes.DUP));
            } else {
                il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                il.add(new FieldInsnNode(Opcodes.GETFIELD, "com/pkmngen/game/Game", field, "Ljava/util/HashMap;"));
            }
            il.add(new InsnNode(Opcodes.ICONST_0 + variant));
            il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ES, "font", "(Ljava/util/Map;I)V", false));
            mn.instructions.insertBefore(r, il);
            changed = true;
            count("fuentes");
        }
        return changed;
    }
}
