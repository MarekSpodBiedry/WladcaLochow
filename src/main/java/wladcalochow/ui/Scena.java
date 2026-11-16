package wladcalochow.ui;

import java.util.List;
import wladcalochow.model.Istota;

/**
 * Sceneria przed lochem: niebo, góry, las, brama i postacie.
 * Krajobraz jest z kolorowych bloków Unicode, a postacie to emoji stojące prosto na trawie.
 */
final class Scena {

    enum Pora { NOC, PORANEK, DZIEN, WIECZOR }

    static final int WYS = 15;
    private static final int HORYZONT = 9;
    private static final int ODSTEP = 4;

    private static final int KAMIEN_A = 0x8E94AA;
    private static final int KAMIEN_B = 0x777D95;
    private static final int KAMIEN_CIEN = 0x5B6079;
    private static final int WEJSCIE = 0x17121F;

    private final Ekran e;
    private final int x0;
    private final int y0;
    private final int w;
    private final Pora pora;
    private final double cien;

    private Scena(Ekran e, int x0, int y0, int w, Pora pora) {
        this.e = e;
        this.x0 = x0;
        this.y0 = y0;
        this.w = w;
        this.pora = pora;
        this.cien = switch (pora) {
            case NOC -> 0.50;
            case WIECZOR -> 0.18;
            case PORANEK -> 0.06;
            case DZIEN -> 0.0;
        };
    }

    static void rysuj(Ekran e, int x0, int y0, int w, Pora pora, List<? extends Istota> lewa,
                      List<? extends Istota> prawa, Istota aktor, Istota cel) {
        Scena scena = new Scena(e, x0, y0, w, pora);
        e.obetnij(x0, y0, w, WYS);
        scena.niebo();
        scena.gory();
        scena.ziemia();
        scena.las();
        scena.brama();
        scena.postacie(lewa, true, aktor, cel);
        scena.postacie(prawa, false, aktor, cel);
        e.zdejmijObciecie();
    }

    /** Kolor z uwzględnieniem pory dnia (wieczorem i w nocy wszystko ciemnieje). */
    private int k(int kolor) {
        return Paleta.przyciemnij(kolor, cien);
    }

    private void znak(int x, int y, String g, int kolor) {
        e.pisz(x0 + x, y0 + y, g, kolor);
    }

    private void znak(int x, int y, String g, int kolor, int tlo) {
        e.pisz(x0 + x, y0 + y, g, kolor, tlo, false);
    }

    private static int szum(int x, int y) {
        int h = x * 73856093 ^ y * 19349663;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return h & 0x7fffffff;
    }

    // ---- niebo ----

    private int kolorNieba(int wiersz) {
        int gora;
        int dol;
        switch (pora) {
            case NOC -> {
                gora = 0x121A3A;
                dol = 0x35407A;
            }
            case PORANEK -> {
                gora = 0x3F74AE;
                dol = 0xF2B988;
            }
            case WIECZOR -> {
                gora = 0x3A3070;
                dol = 0xE8876D;
            }
            default -> {
                gora = 0x2F7EC0;
                dol = 0x9BD0EA;
            }
        }
        return Paleta.mieszaj(gora, dol, wiersz / (double) (HORYZONT - 1));
    }

    private void niebo() {
        for (int r = 0; r < HORYZONT; r++) {
            e.wypelnij(x0, y0 + r, w, 1, kolorNieba(r));
        }
        if (pora == Pora.NOC) {
            for (int r = 0; r < HORYZONT - 2; r++) {
                for (int x = 0; x < w; x++) {
                    int s = szum(x, r) % 41;
                    if (s == 0) {
                        znak(x, r, "·", 0xE9E4C8);
                    } else if (s == 1) {
                        znak(x, r, "*", 0xC9C7E8);
                    }
                }
            }
            cialoNiebieskie((int) (w * 0.70), 1, 0xE8E4CC);
        } else if (pora == Pora.PORANEK) {
            cialoNiebieskie((int) (w * 0.41), 3, 0xF8CE55);
            chmura((int) (w * 0.48), 1);
        } else if (pora == Pora.WIECZOR) {
            cialoNiebieskie((int) (w * 0.60), 5, 0xF29455);
            chmura((int) (w * 0.20), 2);
        } else {
            cialoNiebieskie((int) (w * 0.45), 1, 0xFBD860);
            chmura((int) (w * 0.14), 1);
            chmura((int) (w * 0.62), 2);
        }
    }

    private void cialoNiebieskie(int x, int y, int kolor) {
        znak(x, y, "▄██▄", kolor);
        znak(x, y + 1, "▀██▀", kolor);
    }

    private void chmura(int x, int y) {
        int kolor = Paleta.mieszaj(0xFFFFFF, kolorNieba(y), pora == Pora.DZIEN ? 0.12 : 0.35);
        znak(x + 2, y, "▄██▄▄", kolor);
        znak(x, y + 1, "▀███████▀", kolor);
    }

    // ---- góry ----

    private void gory() {
        int daleka = Paleta.mieszaj(k(0x6A70A0), kolorNieba(HORYZONT - 1), 0.35);
        int bliska = k(0x565C8C);
        szczyt((int) (w * 0.36), 3, daleka);
        szczyt((int) (w * 0.58), 4, daleka);
        szczyt((int) (w * 0.47), 5, bliska);
        szczyt((int) (w * 0.93), 4, bliska);
    }

    private void szczyt(int cx, int wysokosc, int kolor) {
        int snieg = Paleta.mieszaj(0xF1F3F8, kolor, cien * 0.6 + 0.1);
        for (int i = 0; i < wysokosc; i++) {
            int y = HORYZONT - wysokosc + i;
            int barwa = i == 0 ? snieg : kolor;
            znak(cx - i - 1, y, "◢", barwa);
            if (i > 0) {
                znak(cx - i, y, "█".repeat(2 * i), barwa);
            }
            znak(cx + i, y, "◣", Paleta.przyciemnij(barwa, 0.12));
        }
    }

    // ---- ziemia ----

    private int kolorTrawy(int wiersz) {
        return k(Paleta.mieszaj(0x5CB060, 0x3D8C4B, (wiersz - HORYZONT) / (double) (WYS - HORYZONT - 1)));
    }

    private void ziemia() {
        int[] kwiaty = {Paleta.ZLOTO, Paleta.ROZ, 0xF3EFE2};
        for (int r = HORYZONT; r < WYS; r++) {
            int trawa = kolorTrawy(r);
            e.wypelnij(x0, y0 + r, w, 1, trawa);
            for (int x = 0; x < w; x++) {
                int s = szum(x, r + 100);
                if (s % 9 == 0) {
                    znak(x, r, s % 2 == 0 ? "," : "\"", Paleta.przyciemnij(trawa, 0.22));
                } else if (s % 67 == 0) {
                    znak(x, r, "*", k(kwiaty[s % kwiaty.length]));
                }
            }
        }
    }

    // ---- las ----

    private void las() {
        okragleDrzewo(11, 1);
        sosna(5, 8);
        if (w >= 56) {
            okragleDrzewo(21, 0);
            sosna(16, 6);
            sosna(26, 7);
        }
        if (w >= 84) {
            sosna(34, 5);
        }
        sosna(w - 3, 6);
    }

    private void pien(int cx) {
        znak(cx, HORYZONT - 1, "█", k(0x7B4F2E));
        znak(cx, HORYZONT, "█", k(0x6A4326));
    }

    private void sosna(int cx, int wysokosc) {
        int[] zielenie = {0x45AD6E, 0x33935B, 0x26794A};
        pien(cx);
        for (int i = 0; i < wysokosc; i++) {
            int y = HORYZONT - 1 - wysokosc + i;
            int szer = 1 + 2 * ((i % 3) + i / 3);
            int jasny = k(zielenie[Math.min(zielenie.length - 1, i / 3)]);
            int ciemny = Paleta.przyciemnij(jasny, 0.16);
            if (szer == 1) {
                znak(cx, y, "▲", jasny);
                continue;
            }
            int pol = (szer - 1) / 2;
            znak(cx - pol, y, "◢", jasny);
            znak(cx - pol + 1, y, "█".repeat(pol), jasny);
            znak(cx + 1, y, "█".repeat(pol - 1), ciemny);
            znak(cx + pol, y, "◣", ciemny);
        }
    }

    private void okragleDrzewo(int cx, int podniesienie) {
        int jasny = k(0x6CC05B);
        int ciemny = k(0x509F49);
        pien(cx);
        int y = HORYZONT - 4 - podniesienie;
        if (podniesienie > 0) {
            znak(cx, HORYZONT - 2, "█", k(0x7B4F2E));
        }
        znak(cx - 2, y, "▄██", jasny);
        znak(cx + 1, y, "█▄", ciemny);
        znak(cx - 2, y + 1, "███", jasny);
        znak(cx + 1, y + 1, "██", ciemny);
        znak(cx - 2, y + 2, "▀██", jasny);
        znak(cx + 1, y + 2, "█▀", ciemny);
    }

    // ---- brama lochu ----

    private void brama() {
        int szerokosc = 16;
        int gx = w - 23;
        if (gx < 30) {
            gx = Math.max(0, w - szerokosc - 2);
        }
        // chorągiew
        znak(gx + 8, 0, "┃", k(0x9A9FB5));
        znak(gx + 9, 0, "██▶", k(Paleta.CZERWIEN));
        znak(gx + 8, 1, "┃", k(0x9A9FB5));
        // blanki
        for (int i = 0; i < szerokosc; i++) {
            if ((i / 2) % 2 == 0) {
                znak(gx + i, 2, "█", kamien(i, 2, szerokosc));
            }
        }
        // mur
        for (int r = 3; r <= HORYZONT; r++) {
            for (int i = 0; i < szerokosc; i++) {
                znak(gx + i, r, "█", kamien(i, r, szerokosc));
            }
        }
        // szyld
        e.pisz(x0 + gx + 5, y0 + 4, " LOCH ", k(Paleta.ZLOTO), WEJSCIE, true);
        // wejście z łukiem
        znak(gx + 4, 5, "◢", WEJSCIE, kamien(4, 5, szerokosc));
        e.wypelnij(x0 + gx + 5, y0 + 5, 6, 1, WEJSCIE);
        znak(gx + 11, 5, "◣", WEJSCIE, kamien(11, 5, szerokosc));
        e.wypelnij(x0 + gx + 4, y0 + 6, 8, HORYZONT - 5, WEJSCIE);
        // ślepia w ciemności
        znak(gx + 6, 7, "●", k(Paleta.ZLOTO));
        znak(gx + 9, 7, "●", k(Paleta.ZLOTO));
        // pochodnie
        znak(gx + 1, 6, Sprite.OGIEN, Paleta.TEKST, kamien(1, 6, szerokosc));
        znak(gx + 13, 6, Sprite.OGIEN, Paleta.TEKST, kamien(13, 6, szerokosc));
    }

    private int kamien(int i, int wiersz, int szerokosc) {
        if (i == 0 || i == szerokosc - 1) {
            return k(KAMIEN_CIEN);
        }
        int kolor = ((i / 2) + wiersz) % 2 == 0 ? KAMIEN_A : KAMIEN_B;
        if (szum(i, wiersz + 300) % 13 == 0) {
            kolor = 0x6F9A68;
        }
        return k(kolor);
    }

    // ---- postacie ----

    private void postacie(List<? extends Istota> istoty, boolean odLewej, Istota aktor, Istota cel) {
        int wRzedzie = Math.max(1, (w / 2 - 4) / ODSTEP);
        int miejsca = 2 * wRzedzie;
        int pokazane = Math.min(istoty.size(), miejsca);
        for (int i = 0; i < pokazane; i++) {
            Istota istota = istoty.get(i);
            int rzad = i / wRzedzie;
            int kolumna = i % wRzedzie;
            int przesuniecie = 2 + kolumna * ODSTEP + rzad * 2;
            int x = odLewej ? przesuniecie : w - 2 - przesuniecie;
            int y = HORYZONT + 1 + rzad * 2;
            postac(istota, x, y, istota == aktor, istota == cel);
        }
        if (istoty.size() > pokazane) {
            String reszta = "+" + (istoty.size() - pokazane);
            int x = odLewej ? 2 : w - 2 - reszta.length();
            e.pisz(x0 + x, y0 + WYS - 1, reszta, Paleta.TEKST, Ekran.BEZ_ZMIAN, true);
        }
    }

    private void postac(Istota istota, int x, int y, boolean aktywna, boolean atakowana) {
        int trawa = kolorTrawy(y);
        int tlo = trawa;
        if (aktywna) {
            tlo = Paleta.mieszaj(Paleta.ZLOTO, trawa, 0.30);
        } else if (atakowana) {
            tlo = Paleta.mieszaj(Paleta.CZERWIEN, trawa, 0.30);
        }
        znak(x, y, Sprite.wStanie(istota), Paleta.TEKST, tlo);
        if (!istota.wWalce()) {
            return;
        }
        if (istota.czyOgluszona()) {
            znak(x, y + 1, "~~", Paleta.FIOLET);
            return;
        }
        double zycie = istota.procentZycia();
        int pelny = Paleta.zycie(zycie);
        int pusty = Paleta.przyciemnij(kolorTrawy(y + 1), 0.35);
        znak(x, y + 1, "━", pelny);
        znak(x + 1, y + 1, "━", zycie > 0.5 ? pelny : pusty);
    }
}
