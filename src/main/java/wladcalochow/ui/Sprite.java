package wladcalochow.ui;

import wladcalochow.model.Goblin;
import wladcalochow.model.Istota;
import wladcalochow.model.Lucznik;
import wladcalochow.model.Ogr;
import wladcalochow.model.Ork;
import wladcalochow.model.Rycerz;
import wladcalochow.model.Zlodziej;

/** Emoji postaci. Wybrane tak, żeby każde zajmowało w terminalu dokładnie dwie kratki. */
final class Sprite {

    static final String POLEGLY = "💀";
    static final String UCIEKL = "💨";
    static final String ZLOTO = "💰";
    static final String KORONA = "👑";
    static final String FINANSE = "🧮";
    static final String KADRY = "📋";
    static final String OGIEN = "🔥";

    private Sprite() {
    }

    static String emoji(Istota istota) {
        return switch (istota) {
            case Rycerz r -> "🤺";
            case Lucznik l -> "🏹";
            case Zlodziej z -> "🥷";
            case Goblin g -> "👺";
            case Ork o -> "👹";
            case Ogr o -> "🧌";
            default -> "👤";
        };
    }

    /** Emoji postaci z uwzględnieniem jej stanu w walce. */
    static String wStanie(Istota istota) {
        if (!istota.czyZyje()) {
            return POLEGLY;
        }
        return istota.czyUciekla() ? UCIEKL : emoji(istota);
    }
}
