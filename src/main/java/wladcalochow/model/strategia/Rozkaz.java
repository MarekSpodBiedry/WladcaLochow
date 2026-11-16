package wladcalochow.model.strategia;

import wladcalochow.model.MinisterFinansow;
import wladcalochow.model.MinisterPracy;
import wladcalochow.model.Potwor;

/** Decyzja Władcy. Każdy wydany rozkaz zużywa jeden z dziennych rozkazów. */
public sealed interface Rozkaz {

    String nazwa();

    /** Zmiana Ministra Finansów; działa od następnego dnia. */
    record ZmianaMinistraFinansow(MinisterFinansow nowy) implements Rozkaz {
        @Override
        public String nazwa() {
            return "Nowy Minister Finansów: " + nowy;
        }
    }

    /** Zmiana Ministra Pracy; działa od następnego dnia. */
    record ZmianaMinistraPracy(MinisterPracy nowy) implements Rozkaz {
        @Override
        public String nazwa() {
            return "Nowy Minister Pracy: " + nowy;
        }
    }

    /** Jaki procent skarbca wolno najwyżej oddać na rekrutację. */
    record LimitRekrutacji(int procent) implements Rozkaz {
        @Override
        public String nazwa() {
            return "Limit rekrutacji: " + procent + "% skarbca";
        }
    }

    record ZmianaPostawy(Postawa postawa) implements Rozkaz {
        @Override
        public String nazwa() {
            return "Postawa: " + postawa.nazwa();
        }
    }

    record Premia(Potwor potwor) implements Rozkaz {
        @Override
        public String nazwa() {
            return "Premia dla: " + potwor.getImie();
        }
    }

    record Zwolnienie(Potwor potwor) implements Rozkaz {
        @Override
        public String nazwa() {
            return "Zwolnienie: " + potwor.getImie();
        }
    }

    record ZakupUlepszenia(Ulepszenie ulepszenie) implements Rozkaz {
        @Override
        public String nazwa() {
            return "Ulepszenie: " + ulepszenie.nazwa();
        }
    }
}
