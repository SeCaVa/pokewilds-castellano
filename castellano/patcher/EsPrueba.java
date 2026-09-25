import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/**
 * Traduce cada línea de un fichero con Es.tr (o Es.ui si empieza por "ui:") y muestra el resultado.
 * Las líneas no traducidas se marcan con "!!".
 */
public class EsPrueba {
    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, "UTF-8");
        Class<?> es = Class.forName("com.pkmngen.game.Es");
        Field log = es.getDeclaredField("logMissing");
        log.setAccessible(true);
        log.set(null, false);
        Method tr = es.getMethod("tr", String.class);
        Method ui = es.getMethod("ui", String.class);
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(args[0]), StandardCharsets.UTF_8));
        String line;
        int total = 0;
        int bad = 0;
        while ((line = br.readLine()) != null) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            boolean isUi = line.startsWith("ui:");
            String src = isUi ? line.substring(3) : line;
            String r = (String) (isUi ? ui : tr).invoke(null, src);
            total++;
            if (r.equals(src)) {
                bad++;
                out.println("!! " + src);
            } else {
                out.println("   " + src + "\n   -> " + r);
            }
        }
        out.println(total + " pruebas, " + bad + " sin traducir");
    }
}
