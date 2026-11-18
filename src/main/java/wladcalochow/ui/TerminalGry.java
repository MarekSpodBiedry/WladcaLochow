package wladcalochow.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;

/**
 * Cienka warstwa nad JLine: tryb surowy, ekran alternatywny, odczyt klawiszy i myszy.
 *
 * <p>Mysz działa tak jak w Claude Code: tryby 1000 (kliknięcia), 1002 (przeciąganie), 1003 (każdy ruch,
 * do podświetlania) i 1006 (współrzędne SGR). Włącza je {@code terminal.trackMouse}: na Linuksie i macOS
 * wysyła sekwencje do terminala, a na Windows przestawia konsolę tak, żeby JLine dostawał zdarzenia myszy
 * i zamieniał je na te same sekwencje. Bez tego na Windows żadne kliknięcie nie docierało do gry.
 */
final class TerminalGry implements Konsola {

    private static final String WLACZ = "\u001b[?1049h\u001b[?25l\u001b[2J";
    private static final String SGR_WLACZ = "\u001b[?1006h";
    private static final String WYLACZ = "\u001b[?1006l\u001b[?1003l\u001b[?1002l\u001b[?1000l\u001b[0m\u001b[?25h"
            + "\u001b[?1049l";

    private final Terminal terminal;
    private final Attributes zapisaneAtrybuty;
    private final NonBlockingReader czytnik;
    private final boolean pelnyKolor;
    private final Thread sprzatanie;
    private volatile boolean przerwano;
    private boolean zamkniety;

    TerminalGry() throws IOException {
        terminal = TerminalBuilder.builder().system(true).encoding(StandardCharsets.UTF_8).build();
        if (Terminal.TYPE_DUMB.equals(terminal.getType()) || Terminal.TYPE_DUMB_COLOR.equals(terminal.getType())) {
            terminal.close();
            throw new IOException("Gra wymaga prawdziwego terminala (uruchom ją bezpośrednio w oknie terminala).");
        }
        zapisaneAtrybuty = terminal.enterRawMode();
        czytnik = terminal.reader();
        pelnyKolor = wykryjPelnyKolor();
        terminal.handle(Terminal.Signal.INT, sygnal -> przerwano = true);
        sprzatanie = new Thread(this::przywroc);
        Runtime.getRuntime().addShutdownHook(sprzatanie);
        wypisz(WLACZ);
        terminal.trackMouse(Terminal.MouseTracking.Any);
        wypisz(SGR_WLACZ);
    }

    private static boolean wykryjPelnyKolor() {
        String wymuszone = System.getProperty("wladca.kolory", System.getenv("WLADCA_KOLORY"));
        if (wymuszone != null) {
            return !wymuszone.trim().equals("256");
        }
        String colorterm = String.valueOf(System.getenv("COLORTERM")).toLowerCase();
        String term = String.valueOf(System.getenv("TERM")).toLowerCase();
        String program = String.valueOf(System.getenv("TERM_PROGRAM"));
        return colorterm.contains("truecolor") || colorterm.contains("24bit")
                || System.getenv("WT_SESSION") != null
                || term.contains("kitty") || term.contains("direct") || term.contains("alacritty")
                || term.contains("ghostty") || term.contains("wezterm")
                || program.equals("iTerm.app") || program.equals("vscode") || program.equals("WezTerm")
                || program.equals("ghostty") || program.equals("WarpTerminal");
    }

    @Override
    public boolean pelnyKolor() {
        return pelnyKolor;
    }

    @Override
    public int szer() {
        return terminal.getSize().getColumns();
    }

    @Override
    public int wys() {
        return terminal.getSize().getRows();
    }

    @Override
    public void wypisz(String tekst) {
        terminal.writer().print(tekst);
        terminal.writer().flush();
    }

    /** Czeka najwyżej podany czas na jedno zdarzenie wejścia. */
    @Override
    public Wejscie czytaj(long milisekundy) throws IOException {
        if (przerwano) {
            return new Wejscie.Koniec();
        }
        int c = czytnik.read(milisekundy);
        if (c == NonBlockingReader.READ_EXPIRED) {
            return przerwano ? new Wejscie.Koniec() : new Wejscie.Brak();
        }
        if (c < 0 || c == 3) {
            return new Wejscie.Koniec();
        }
        return switch (c) {
            case 27 -> sekwencja();
            case 13, 10 -> new Wejscie.Klawisz(Wejscie.Rodzaj.ENTER);
            case 9 -> new Wejscie.Klawisz(Wejscie.Rodzaj.TAB);
            case 127, 8 -> new Wejscie.Klawisz(Wejscie.Rodzaj.BACKSPACE);
            default -> c >= 32 ? new Wejscie.Znak((char) c) : new Wejscie.Brak();
        };
    }

    private int nastepny() throws IOException {
        return czytnik.read(50L);
    }

    private Wejscie sekwencja() throws IOException {
        int c = nastepny();
        if (c < 0) {
            return new Wejscie.Klawisz(Wejscie.Rodzaj.ESC);
        }
        if (c == 'O') {
            return strzalka(nastepny());
        }
        if (c != '[') {
            return new Wejscie.Brak();
        }
        int d = nastepny();
        if (d == 'M') {
            // Starszy format zgłoszeń myszy: trzy bajty przesunięte o 32.
            int przycisk = nastepny() - 32;
            int x = nastepny() - 32;
            int y = nastepny() - 32;
            return klik(przycisk, x, y, true);
        }
        StringBuilder parametry = new StringBuilder();
        while (d >= 0 && (d < 0x40 || d > 0x7E)) {
            parametry.append((char) d);
            d = nastepny();
        }
        if (d < 0) {
            return new Wejscie.Brak();
        }
        if ((d == 'M' || d == 'm') && parametry.length() > 0 && parametry.charAt(0) == '<') {
            String[] pola = parametry.substring(1).split(";");
            if (pola.length == 3) {
                try {
                    return klik(Integer.parseInt(pola[0]), Integer.parseInt(pola[1]), Integer.parseInt(pola[2]),
                            d == 'M');
                } catch (NumberFormatException e) {
                    return new Wejscie.Brak();
                }
            }
            return new Wejscie.Brak();
        }
        if (d == '~') {
            return switch (parametry.toString()) {
                case "5" -> new Wejscie.Klawisz(Wejscie.Rodzaj.STRONA_W_GORE);
                case "6" -> new Wejscie.Klawisz(Wejscie.Rodzaj.STRONA_W_DOL);
                default -> new Wejscie.Brak();
            };
        }
        return strzalka(d);
    }

    private static Wejscie strzalka(int kod) {
        return switch (kod) {
            case 'A' -> new Wejscie.Klawisz(Wejscie.Rodzaj.GORA);
            case 'B' -> new Wejscie.Klawisz(Wejscie.Rodzaj.DOL);
            case 'C' -> new Wejscie.Klawisz(Wejscie.Rodzaj.PRAWO);
            case 'D' -> new Wejscie.Klawisz(Wejscie.Rodzaj.LEWO);
            default -> new Wejscie.Brak();
        };
    }

    /**
     * Zamienia zgłoszenie myszy na zdarzenie gry: ruch (podświetlanie), kółko (przewijanie) albo kliknięcie
     * lewym przyciskiem. Puszczenie przycisku i inne przyciski są pomijane. Współrzędne od 1, gra liczy od 0.
     */
    static Wejscie klik(int przycisk, int x, int y, boolean nacisniecie) {
        if (x < 1 || y < 1) {
            return new Wejscie.Brak();
        }
        boolean ruch = (przycisk & 32) != 0;
        boolean kolko = (przycisk & 64) != 0;
        if (kolko) {
            return nacisniecie ? new Wejscie.Kolko((przycisk & 1) == 0 ? -1 : 1, x - 1, y - 1) : new Wejscie.Brak();
        }
        if (ruch) {
            return new Wejscie.Ruch(x - 1, y - 1);
        }
        // W starym formacie (bez SGR) puszczenie przycisku to kod 3.
        boolean lewy = (przycisk & 3) == 0;
        if (!nacisniecie || !lewy) {
            return new Wejscie.Ruch(x - 1, y - 1);
        }
        return new Wejscie.Klik(x - 1, y - 1);
    }

    private synchronized void przywroc() {
        if (zamkniety) {
            return;
        }
        zamkniety = true;
        try {
            terminal.trackMouse(Terminal.MouseTracking.Off);
            wypisz(WYLACZ);
            terminal.setAttributes(zapisaneAtrybuty);
        } catch (RuntimeException e) {
            // terminal mógł już zostać zamknięty
        }
    }

    @Override
    public void close() throws IOException {
        przywroc();
        try {
            Runtime.getRuntime().removeShutdownHook(sprzatanie);
        } catch (IllegalStateException e) {
            // JVM już się zamyka
        }
        terminal.close();
    }
}
