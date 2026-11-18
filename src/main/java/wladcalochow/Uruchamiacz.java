package wladcalochow;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Otwiera okno terminala z grą, gdy plik .jar został uruchomiony bez terminala (np. dwuklikiem).
 * Gra potrzebuje prawdziwego terminala, więc uruchamia samą siebie jeszcze raz, już w jego oknie.
 */
public final class Uruchamiacz {

    /** Opcja przekazywana grze uruchomionej w nowym oknie, żeby nie próbowała otwierać kolejnego. */
    public static final String OPCJA_W_OKNIE = "--w-oknie";

    /** Opcja wymuszająca tryb kolorów: 24 (pełne kolory) albo 256. */
    public static final String OPCJA_KOLORY = "--kolory";

    /** Rozmiar okna, o który prosimy terminal: z zapasem ponad minimum gry. */
    public static final int KOLUMNY = 124;
    public static final int WIERSZE = 38;

    private static final String TYTUL = "Wladca Lochow";
    private static final long CZAS_NA_START_MS = 1200;

    public enum SystemOperacyjny { WINDOWS, MACOS, LINUX }

    private Uruchamiacz() {
    }

    public static SystemOperacyjny wykryjSystem() {
        String nazwa = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (nazwa.contains("win")) {
            return SystemOperacyjny.WINDOWS;
        }
        return nazwa.contains("mac") || nazwa.contains("darwin") ? SystemOperacyjny.MACOS : SystemOperacyjny.LINUX;
    }

    /**
     * Polecenia otwierające terminal z grą, od najlepszego do najprostszego.
     *
     * @param polecenieGry program i argumenty, które mają wystartować w nowym oknie
     * @param katalog      katalog roboczy gry w nowym oknie (Windows Terminal dostaje go wprost), może być null
     * @param dostepny     sprawdza, czy program o danej nazwie jest zainstalowany
     */
    public static List<List<String>> polecenia(SystemOperacyjny system, List<String> polecenieGry, Path katalog,
                                               Predicate<String> dostepny) {
        List<List<String>> wynik = new ArrayList<>();
        switch (system) {
            case WINDOWS -> {
                if (dostepny.test("wt.exe")) {
                    // Windows Terminal: pełne kolory i emoji; okno zmaksymalizowane, żeby gra się zmieściła.
                    // Nowa karta nie dziedziczy katalogu roboczego, więc dostaje go przez -d.
                    List<String> wt = new ArrayList<>(List.of("wt.exe", "-M", "new-tab", "--title", TYTUL));
                    if (katalog != null) {
                        wt.addAll(List.of("-d", katalog.toString()));
                    }
                    wynik.add(zlacz(wt, polecenieGry));
                }
                // Zwykła konsola Windows: "start" otwiera nowe, zmaksymalizowane okno w bieżącym katalogu.
                wynik.add(zlacz(List.of("cmd.exe", "/c", "start", TYTUL, "/MAX"), polecenieGry));
            }
            case MACOS -> wynik.add(List.of("osascript",
                    "-e", "tell application \"Terminal\"",
                    "-e", "activate",
                    "-e", "set karta to do script \"" + dlaAppleScript(dlaPowloki(polecenieGry) + "; exit") + "\"",
                    "-e", "set number of columns of karta to " + KOLUMNY,
                    "-e", "set number of rows of karta to " + WIERSZE,
                    "-e", "end tell"));
            case LINUX -> {
                String geometria = KOLUMNY + "x" + WIERSZE;
                dodaj(wynik, dostepny, "gnome-terminal", List.of("--geometry=" + geometria, "--title=" + TYTUL, "--"),
                        polecenieGry);
                dodaj(wynik, dostepny, "konsole", List.of("-e"), polecenieGry);
                dodaj(wynik, dostepny, "xfce4-terminal", List.of("--geometry=" + geometria, "--title=" + TYTUL, "-x"),
                        polecenieGry);
                dodaj(wynik, dostepny, "kitty", List.of("--title", TYTUL), polecenieGry);
                dodaj(wynik, dostepny, "alacritty", List.of("--title", TYTUL, "-e"), polecenieGry);
                dodaj(wynik, dostepny, "wezterm", List.of("start", "--"), polecenieGry);
                dodaj(wynik, dostepny, "ghostty", List.of("-e"), polecenieGry);
                dodaj(wynik, dostepny, "foot", List.of("--title=" + TYTUL), polecenieGry);
                dodaj(wynik, dostepny, "mate-terminal", List.of("--geometry=" + geometria, "--title=" + TYTUL, "-x"),
                        polecenieGry);
                dodaj(wynik, dostepny, "tilix", List.of("--geometry=" + geometria, "-e"), polecenieGry);
                dodaj(wynik, dostepny, "lxterminal", List.of("--geometry=" + geometria, "-e"), polecenieGry);
                dodaj(wynik, dostepny, "terminator", List.of("-m", "-x"), polecenieGry);
                // xterm: skalowalna czcionka, bo domyślna bitmapowa nie ma polskich znaków ani ramek.
                // xterm obsługuje pełne kolory, ale tego nie ogłasza, więc mówimy o tym grze wprost.
                dodaj(wynik, dostepny, "xterm", List.of("-geometry", geometria, "-fa", "Monospace", "-fs", "11",
                        "-T", TYTUL, "-e"), zlacz(polecenieGry, List.of(OPCJA_KOLORY, "24")));
                // Domyślny terminal systemu: najpierw z rozmiarem okna, a gdy go nie rozumie, bez.
                dodaj(wynik, dostepny, "x-terminal-emulator", List.of("-geometry", geometria, "-e"), polecenieGry);
                dodaj(wynik, dostepny, "x-terminal-emulator", List.of("-e"), polecenieGry);
            }
        }
        return wynik;
    }

    private static void dodaj(List<List<String>> wynik, Predicate<String> dostepny, String program,
                              List<String> opcje, List<String> polecenieGry) {
        if (dostepny.test(program)) {
            List<String> poczatek = new ArrayList<>();
            poczatek.add(program);
            poczatek.addAll(opcje);
            wynik.add(zlacz(poczatek, polecenieGry));
        }
    }

    private static List<String> zlacz(List<String> poczatek, List<String> reszta) {
        List<String> calosc = new ArrayList<>(poczatek);
        calosc.addAll(reszta);
        return calosc;
    }

    /** Skleja polecenie w jeden napis dla powłoki uniksowej; każdy argument w pojedynczych cudzysłowach. */
    static String dlaPowloki(List<String> polecenie) {
        StringBuilder sb = new StringBuilder();
        for (String argument : polecenie) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append('\'').append(argument.replace("'", "'\\''")).append('\'');
        }
        return sb.toString();
    }

    /** Zabezpiecza tekst wstawiany do napisu w AppleScripcie. */
    static String dlaAppleScript(String tekst) {
        return tekst.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Opcje Javy dla okna gry: mały limit pamięci i prosty odśmiecacz, który oddaje pamięć systemowi. */
    public static final List<String> OPCJE_JVM = List.of("-Xmx160m", "-XX:+UseSerialGC");

    /** Polecenie, które uruchamia tę samą grę jeszcze raz: ta sama Java, ten sam plik .jar. */
    public static List<String> polecenieGry(Path java, Path jar, List<String> argumenty) {
        // Limit pamięci: bez niego Java bierze na zapas dużą część RAM-u (u Marka 700 MB). Grze wystarcza ułamek.
        List<String> polecenie = new ArrayList<>(List.of(java.toString()));
        polecenie.addAll(OPCJE_JVM);
        polecenie.addAll(List.of("-jar", jar.toString(), OPCJA_W_OKNIE));
        polecenie.addAll(argumenty);
        return polecenie;
    }

    /** Plik .jar, z którego działa gra, albo null, gdy gra nie została uruchomiona z pliku .jar. */
    public static Path plikJar() {
        try {
            Path zrodlo = Path.of(Uruchamiacz.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isRegularFile(zrodlo) && zrodlo.toString().endsWith(".jar") ? zrodlo : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Program java z tej samej instalacji, która uruchomiła grę (na Windows java.exe, nie javaw.exe). */
    public static Path programJava(SystemOperacyjny system) {
        String nazwa = system == SystemOperacyjny.WINDOWS ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", nazwa);
    }

    /** Czy program o tej nazwie leży w którymś z katalogów ze zmiennej PATH. */
    public static boolean naSciezce(String program) {
        String sciezka = System.getenv("PATH");
        if (sciezka == null) {
            return false;
        }
        for (String katalog : sciezka.split(File.pathSeparator)) {
            if (!katalog.isBlank() && Files.isExecutable(Path.of(katalog, program))) {
                return true;
            }
        }
        return false;
    }

    /** Folder systemowy Windows (zwykle C:\Windows\System32), z którego bierzemy cmd.exe i PowerShella. */
    private static String system32() {
        String windows = System.getenv("SystemRoot");
        return (windows == null || windows.isBlank() ? "C:\\Windows" : windows) + "\\System32";
    }

    /** Polecenie otwierające na Windows nowe okno PowerShella z instalatorem, który aktualizuje grę. */
    public static List<String> polecenieAktualizacji() {
        return polecenieAktualizacji(system32() + "\\cmd.exe");
    }

    static List<String> polecenieAktualizacji(String cmd) {
        return List.of(cmd, "/c", "start", TYTUL, "powershell.exe", "-NoProfile", "-ExecutionPolicy",
                "Bypass", "-Command", Aktualizacje.POLECENIE_INSTALATORA_BEZPOSREDNIE);
    }

    /** Zapasowa droga bez cmd.exe: PowerShell sam otwiera nowe okno PowerShella z instalatorem. */
    static List<String> polecenieAktualizacjiPrzezPowerShella() {
        String powershell = system32() + "\\WindowsPowerShell\\v1.0\\powershell.exe";
        String instalator = Aktualizacje.POLECENIE_INSTALATORA_BEZPOSREDNIE.replace("'", "''");
        return List.of(powershell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-Command",
                "Start-Process -FilePath '" + powershell + "' -ArgumentList '-NoProfile','-ExecutionPolicy','Bypass',"
                        + "'-Command','" + instalator + "'");
    }

    /**
     * Uruchamia instalator w nowym oknie. Próbuje kolejno: cmd.exe z pełną ścieżką, PowerShella z pełną
     * ścieżką, cmd.exe szukany po PATH.
     *
     * @return pusty wynik, gdy instalator wystartował; w przeciwnym razie błędy wszystkich prób, do pokazania graczowi
     */
    public static Optional<String> uruchomAktualizacje() {
        if (wykryjSystem() != SystemOperacyjny.WINDOWS) {
            return Optional.of("samoczynna aktualizacja działa tylko na Windows");
        }
        List<String> bledy = new ArrayList<>();
        for (List<String> polecenie : List.of(polecenieAktualizacji(), polecenieAktualizacjiPrzezPowerShella(),
                polecenieAktualizacji("cmd.exe"))) {
            try {
                new ProcessBuilder(polecenie)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
                return Optional.empty();
            } catch (IOException | RuntimeException e) {
                bledy.add(String.valueOf(e.getMessage()));
            }
        }
        return Optional.of(String.join("; ", bledy));
    }

    /**
     * Próbuje otworzyć okno terminala z grą.
     *
     * @return true, jeśli któryś terminal udało się uruchomić
     */
    public static boolean otworzOkno(List<String> argumenty) {
        Path jar = plikJar();
        if (jar == null) {
            return false;
        }
        SystemOperacyjny system = wykryjSystem();
        // Na Windows Java czyta argumenty w stronie kodowej systemu: pełna ścieżka z polskimi literami
        // (np. w nazwie użytkownika) zamieniłaby się w znaki zapytania. Dlatego gra dostaje samą nazwę
        // pliku, a folder gry jest katalogiem roboczym.
        boolean windows = system == SystemOperacyjny.WINDOWS && jar.getParent() != null;
        Path katalog = windows ? jar.getParent() : null;
        List<String> gra = polecenieGry(programJava(system), windows ? jar.getFileName() : jar, argumenty);
        for (List<String> polecenie : polecenia(system, gra, katalog, Uruchamiacz::naSciezce)) {
            try {
                ProcessBuilder budowa = new ProcessBuilder(polecenie);
                if (katalog != null) {
                    budowa.directory(katalog.toFile());
                }
                Process proces = budowa
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
                // Terminal, który od razu kończy się błędem (np. brak ekranu), nie otworzył okna.
                if (!proces.waitFor(CZAS_NA_START_MS, TimeUnit.MILLISECONDS) || proces.exitValue() == 0) {
                    return true;
                }
            } catch (IOException e) {
                // ten terminal nie zadziałał, próbujemy następnego
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}
