package wladcalochow.ui;

import wladcalochow.model.Zdarzenie.Ton;

/** Kolory gry: żywe, ale nie neonowe. Wszystkie jako 0xRRGGBB. */
final class Paleta {

    static final int TLO = 0x14161F;
    static final int PANEL = 0x1B1F2E;
    static final int PANEL_JASNY = 0x2A3049;
    static final int RAMKA = 0x4A5278;
    static final int NAGLOWEK = 0x242A42;

    static final int TEKST = 0xECE6D6;
    static final int TEKST_SLABY = 0x8D94AF;
    static final int TEKST_CIEMNY = 0x171922;

    static final int ZLOTO = 0xF0B840;
    static final int CZERWIEN = 0xE2574C;
    static final int ZIELEN = 0x4DB37E;
    static final int NIEBIESKI = 0x4FA3DB;
    static final int FIOLET = 0x9E7FDB;
    static final int POMARANCZ = 0xEC8B45;
    static final int TURKUS = 0x38B6A8;
    static final int ROZ = 0xDB7399;

    static final int GUZIK = 0x2E3654;
    static final int GUZIK_GLOWNY = 0x2E8B62;
    static final int GUZIK_WYLACZONY = 0x20243A;

    private Paleta() {
    }

    /** Miesza dwa kolory: t = 0 daje a, t = 1 daje b. */
    static int mieszaj(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    static int przyciemnij(int kolor, double ile) {
        return mieszaj(kolor, 0x000000, ile);
    }

    static int rozjasnij(int kolor, double ile) {
        return mieszaj(kolor, 0xFFFFFF, ile);
    }

    static int ton(Ton ton) {
        return switch (ton) {
            case NEUTRALNY -> TEKST;
            case DOBRY -> ZIELEN;
            case ZLY -> CZERWIEN;
            case ZLOTO -> ZLOTO;
            case NAGLOWEK -> POMARANCZ;
        };
    }

    /** Kolor paska życia: zielony, złoty albo czerwony. */
    static int zycie(double procent) {
        if (procent > 0.6) {
            return ZIELEN;
        }
        return procent > 0.3 ? ZLOTO : CZERWIEN;
    }

    static int morale(double procent) {
        return procent >= 0.5 ? NIEBIESKI : FIOLET;
    }
}
