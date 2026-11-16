package wladcalochow.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Bufor ekranu: siatka komórek (znak, kolor, tło). Rysujemy do bufora, a potem zamieniamy go
 * na sekwencje ANSI, wysyłając tylko te wiersze, które zmieniły się od poprzedniej klatki.
 */
final class Ekran {

    /** Wartość tła oznaczająca "zostaw tło, które już jest w komórce". */
    static final int BEZ_ZMIAN = -1;

    final int szer;
    final int wys;
    private final String[] glif;
    private final int[] kolor;
    private final int[] tlo;
    private final boolean[] gruby;
    private int ox0;
    private int oy0;
    private int ox1;
    private int oy1;

    Ekran(int szer, int wys, int kolorTla) {
        this.szer = szer;
        this.wys = wys;
        glif = new String[szer * wys];
        kolor = new int[szer * wys];
        tlo = new int[szer * wys];
        gruby = new boolean[szer * wys];
        Arrays.fill(glif, " ");
        Arrays.fill(kolor, Paleta.TEKST);
        Arrays.fill(tlo, kolorTla);
        zdejmijObciecie();
    }

    /** Ogranicza rysowanie do prostokąta (np. do obszaru sceny). */
    void obetnij(int x, int y, int w, int h) {
        ox0 = Math.max(0, x);
        oy0 = Math.max(0, y);
        ox1 = Math.min(szer, x + w);
        oy1 = Math.min(wys, y + h);
    }

    void zdejmijObciecie() {
        ox0 = 0;
        oy0 = 0;
        ox1 = szer;
        oy1 = wys;
    }

    private boolean widac(int x, int y) {
        return x >= ox0 && x < ox1 && y >= oy0 && y < oy1;
    }

    private void komorka(int x, int y, String g, int kolorZnaku, int kolorTla, boolean pogrubiony) {
        int i = y * szer + x;
        if (glif[i].isEmpty() && x > 0) {
            glif[i - 1] = " ";
        }
        if (x + 1 < szer && glif[i + 1].isEmpty()) {
            glif[i + 1] = " ";
        }
        glif[i] = g;
        kolor[i] = kolorZnaku;
        if (kolorTla != BEZ_ZMIAN) {
            tlo[i] = kolorTla;
        }
        gruby[i] = pogrubiony;
    }

    /** Pisze tekst, zachowując tło komórek. Zwraca pozycję x za ostatnim znakiem. */
    int pisz(int x, int y, String tekst, int kolorZnaku) {
        return pisz(x, y, tekst, kolorZnaku, BEZ_ZMIAN, false);
    }

    int pisz(int x, int y, String tekst, int kolorZnaku, int kolorTla, boolean pogrubiony) {
        int i = 0;
        int n = tekst.length();
        while (i < n) {
            int cp = tekst.codePointAt(i);
            i += Character.charCount(cp);
            StringBuilder g = new StringBuilder().appendCodePoint(cp);
            while (i < n && zerowa(tekst.codePointAt(i))) {
                int dodatek = tekst.codePointAt(i);
                g.appendCodePoint(dodatek);
                i += Character.charCount(dodatek);
            }
            int w = szerokoscZnaku(cp);
            if (w == 0) {
                continue;
            }
            if (widac(x, y) && (w == 1 || widac(x + 1, y))) {
                komorka(x, y, g.toString(), kolorZnaku, kolorTla, pogrubiony);
                if (w == 2) {
                    int j = y * szer + x + 1;
                    if (x + 2 < szer && glif[j + 1].isEmpty()) {
                        glif[j + 1] = " ";
                    }
                    glif[j] = "";
                    kolor[j] = kolorZnaku;
                    tlo[j] = tlo[j - 1];
                    gruby[j] = false;
                }
            }
            x += w;
        }
        return x;
    }

    /** Wypełnia prostokąt kolorem tła (i czyści znaki). */
    void wypelnij(int x, int y, int w, int h, int kolorTla) {
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) {
                if (widac(xx, yy)) {
                    komorka(xx, yy, " ", Paleta.TEKST, kolorTla, false);
                }
            }
        }
    }

    int tloW(int x, int y) {
        if (x < 0 || x >= szer || y < 0 || y >= wys) {
            return Paleta.TLO;
        }
        return tlo[y * szer + x];
    }

    /** Ramka z zaokrąglonymi rogami, wypełniona kolorem panelu, z opcjonalnym tytułem. */
    void ramka(int x, int y, int w, int h, int kolorRamki, int kolorTla, String tytul, int kolorTytulu) {
        if (w < 2 || h < 2) {
            return;
        }
        wypelnij(x, y, w, h, kolorTla);
        String poziom = "─".repeat(w - 2);
        pisz(x, y, "╭" + poziom + "╮", kolorRamki);
        pisz(x, y + h - 1, "╰" + poziom + "╯", kolorRamki);
        for (int yy = y + 1; yy < y + h - 1; yy++) {
            pisz(x, yy, "│", kolorRamki);
            pisz(x + w - 1, yy, "│", kolorRamki);
        }
        if (tytul != null && !tytul.isEmpty()) {
            pisz(x + 2, y, " " + przytnij(tytul, w - 6) + " ", kolorTytulu, BEZ_ZMIAN, true);
        }
    }

    // ---- zamiana na ANSI ----

    String ansi(Ekran poprzedni, boolean pelnyKolor) {
        StringBuilder sb = new StringBuilder(szer * wys * 6);
        sb.append("\u001b[?2026h");
        boolean porownuj = poprzedni != null && poprzedni.szer == szer && poprzedni.wys == wys;
        for (int y = 0; y < wys; y++) {
            if (porownuj && wierszBezZmian(poprzedni, y)) {
                continue;
            }
            pozycja(sb, y, 0);
            int aKolor = -2;
            int aTlo = -2;
            boolean aGruby = false;
            for (int x = 0; x < szer; x++) {
                int i = y * szer + x;
                String g = glif[i];
                if (g.isEmpty()) {
                    continue;
                }
                if (kolor[i] != aKolor || tlo[i] != aTlo || gruby[i] != aGruby) {
                    aKolor = kolor[i];
                    aTlo = tlo[i];
                    aGruby = gruby[i];
                    sgr(sb, aKolor, aTlo, aGruby, pelnyKolor);
                }
                int cp = g.codePointAt(0);
                if (szerokoscZnaku(cp) == 2) {
                    // Najpierw tło pod obie kratki, potem znak i jawny powrót kursora:
                    // układ nie rozjedzie się, nawet gdy terminal inaczej liczy szerokość emoji.
                    sb.append("  ");
                    pozycja(sb, y, x);
                    sb.append(g);
                    pozycja(sb, y, x + 2);
                } else {
                    sb.append(g);
                    if (cp >= 0x2600) {
                        pozycja(sb, y, x + 1);
                    }
                }
            }
            sb.append("\u001b[0m");
        }
        sb.append("\u001b[?2026l");
        return sb.toString();
    }

    private boolean wierszBezZmian(Ekran inny, int y) {
        for (int i = y * szer; i < (y + 1) * szer; i++) {
            if (kolor[i] != inny.kolor[i] || tlo[i] != inny.tlo[i] || gruby[i] != inny.gruby[i]
                    || !glif[i].equals(inny.glif[i])) {
                return false;
            }
        }
        return true;
    }

    private static void pozycja(StringBuilder sb, int y, int x) {
        sb.append("\u001b[").append(y + 1).append(';').append(x + 1).append('H');
    }

    private static void sgr(StringBuilder sb, int kolorZnaku, int kolorTla, boolean pogrubiony, boolean pelnyKolor) {
        sb.append("\u001b[0;");
        if (pogrubiony) {
            sb.append("1;");
        }
        if (pelnyKolor) {
            sb.append("38;2;").append((kolorZnaku >> 16) & 255).append(';').append((kolorZnaku >> 8) & 255)
                    .append(';').append(kolorZnaku & 255);
            sb.append(";48;2;").append((kolorTla >> 16) & 255).append(';').append((kolorTla >> 8) & 255)
                    .append(';').append(kolorTla & 255);
        } else {
            sb.append("38;5;").append(do256(kolorZnaku)).append(";48;5;").append(do256(kolorTla));
        }
        sb.append('m');
    }

    /** Najbliższy kolor z palety 256 kolorów dla terminali bez 24-bitowego koloru. */
    static int do256(int rgb) {
        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = rgb & 255;
        if (Math.abs(r - g) < 10 && Math.abs(g - b) < 10) {
            int szary = (r + g + b) / 3;
            if (szary < 8) {
                return 16;
            }
            return szary > 238 ? 231 : 232 + (szary - 8) / 10;
        }
        return 16 + 36 * kostka(r) + 6 * kostka(g) + kostka(b);
    }

    private static int kostka(int v) {
        if (v < 48) {
            return 0;
        }
        return v < 115 ? 1 : (v - 35) / 40;
    }

    // ---- pomocnicze operacje na tekście ----

    static boolean zerowa(int cp) {
        return cp == 0xFE0F || cp == 0xFE0E || cp == 0x200D || (cp >= 0x0300 && cp <= 0x036F);
    }

    /** Ile kratek terminala zajmuje znak: emoji i znaki wschodnioazjatyckie zajmują dwie. */
    static int szerokoscZnaku(int cp) {
        if (zerowa(cp)) {
            return 0;
        }
        if (cp >= 0x1F000 || (cp >= 0x1100 && cp <= 0x115F) || (cp >= 0x2E80 && cp <= 0xA4CF)
                || (cp >= 0xAC00 && cp <= 0xD7A3) || (cp >= 0xF900 && cp <= 0xFAFF)
                || (cp >= 0xFF00 && cp <= 0xFF60)) {
            return 2;
        }
        return 1;
    }

    static int szerokosc(String tekst) {
        return tekst.codePoints().map(Ekran::szerokoscZnaku).sum();
    }

    /** Skraca tekst do podanej szerokości, dodając wielokropek. */
    static String przytnij(String tekst, int maks) {
        if (maks <= 0) {
            return "";
        }
        if (szerokosc(tekst) <= maks) {
            return tekst;
        }
        StringBuilder sb = new StringBuilder();
        int w = 0;
        for (int cp : tekst.codePoints().toArray()) {
            int z = szerokoscZnaku(cp);
            if (w + z > maks - 1) {
                break;
            }
            sb.appendCodePoint(cp);
            w += z;
        }
        return sb + "…";
    }

    /** Dopełnia tekst spacjami z prawej do podanej szerokości (albo go przycina). */
    static String dopelnij(String tekst, int docelowa) {
        String przyciety = przytnij(tekst, docelowa);
        return przyciety + " ".repeat(Math.max(0, docelowa - szerokosc(przyciety)));
    }

    /** Łamie tekst na wiersze nie szersze niż podana szerokość. */
    static List<String> zawin(String tekst, int maks) {
        List<String> wiersze = new ArrayList<>();
        StringBuilder wiersz = new StringBuilder();
        int w = 0;
        for (String slowo : tekst.split(" ")) {
            int ws = szerokosc(slowo);
            if (w > 0 && w + 1 + ws > maks) {
                wiersze.add(wiersz.toString());
                wiersz.setLength(0);
                w = 0;
            }
            while (ws > maks) {
                int ciecie = slowo.offsetByCodePoints(0, Math.min(maks, slowo.codePointCount(0, slowo.length())));
                wiersze.add(slowo.substring(0, ciecie));
                slowo = slowo.substring(ciecie);
                ws = szerokosc(slowo);
            }
            if (w > 0) {
                wiersz.append(' ');
                w++;
            }
            wiersz.append(slowo);
            w += ws;
        }
        if (w > 0 || wiersze.isEmpty()) {
            wiersze.add(wiersz.toString());
        }
        return wiersze;
    }
}
