package wladcalochow.silnik;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import wladcalochow.model.Bohater;
import wladcalochow.model.BylejakiMinisterPracy;
import wladcalochow.model.Goblin;
import wladcalochow.model.Lucznik;
import wladcalochow.model.MinisterFinansow;
import wladcalochow.model.MinisterPracy;
import wladcalochow.model.Ogr;
import wladcalochow.model.Ork;
import wladcalochow.model.Potwor;
import wladcalochow.model.RamboMinisterPracy;
import wladcalochow.model.RozrzutnyMinisterFinansow;
import wladcalochow.model.Rycerz;
import wladcalochow.model.ZachowawczyMinisterFinansow;
import wladcalochow.model.Zlodziej;

/** Jedyne miejsce, w którym powstają istoty i ministrowie. Każdy obiekt jest nowy i unikatowy. */
public final class FabrykaIstot {

    /** Najdłuższe imię istoty; tyle miejsca rezerwują listy na ekranie. */
    public static final int MAX_DLUGOSC_IMIENIA = 13;

    // Pule są większe niż największa liczba istot jednego rodzaju naraz (loch + kandydaci, atak),
    // więc imiona się nie powtarzają i nie trzeba do nich dopisywać numerów.
    private static final String[] GOBLINY = {"Zgrzyt", "Pchełka", "Smark", "Chichot", "Gnilec", "Wypłosz",
        "Kleks", "Ścierwek", "Bzdryl", "Purchawka", "Glut", "Świst", "Okruch", "Chrzęst", "Pisk", "Mamlas",
        "Ropuch", "Kikut", "Szczypek", "Brudas"};
    private static final String[] ORKI = {"Gruk", "Morg", "Urzak", "Brzdęk", "Gromokieł", "Zgniot", "Harat",
        "Burgul", "Krwawiec", "Drągal", "Łomot", "Szczerb", "Warchoł", "Rozpruwacz", "Dzik", "Trzask",
        "Kark", "Gburak", "Tasak", "Zadzior"};
    private static final String[] OGRY = {"Buła", "Tłuczek", "Wielgus", "Kafar", "Mamrot", "Głaz", "Bęben",
        "Kloc", "Gniotek", "Baniak", "Młot", "Kolos", "Brzuchacz", "Grzmot", "Taran", "Garb", "Zwalisty",
        "Moloch", "Byk", "Piec"};
    private static final String[] RYCERZE = {"Sir Bolko", "Sir Zawisza", "Sir Mściwój", "Sir Gniewko",
        "Sir Dobromir", "Sir Jaromir", "Sir Racibor", "Sir Zbyszko", "Sir Bożydar", "Sir Lutobor",
        "Sir Sędziwój", "Sir Wszebor", "Sir Niemir", "Sir Kazimir"};
    private static final String[] LUCZNICY = {"Bystra Jagna", "Cichy Wit", "Celna Dobrawa", "Leśny Bor",
        "Wartki Miłosz", "Smukła Sława", "Sokoli Radek", "Czujna Wanda", "Zwinny Lech", "Rącza Mila",
        "Baczna Zofia", "Prędki Olek", "Cicha Bogna", "Strzelny Jur"};
    private static final String[] ZLODZIEJE = {"Lepki Staś", "Cień", "Szybki Lolo", "Wytrych", "Kieszonka",
        "Myszka", "Paluch", "Śliski Zenek", "Kocur", "Szmer", "Fant", "Długa Ręka", "Łasica", "Cichy Krok"};
    private static final String[] MINISTROWIE_FINANSOW = {"Hieronim Skąpiec", "Baron Grosik",
        "Hrabina Sakiewka", "Onufry Liczykrupa", "Walenty Dukat", "Pani Moneta", "Ignacy Talar",
        "Zenobiusz Rachmistrz", "Księgowa Brzęczek", "Kanclerz Trzos"};
    private static final String[] MINISTROWIE_PRACY = {"Sierżant Pięść", "Kapral Młot", "Pan Ziewak",
        "Ciotka Leniuszka", "Major Twardziel", "Brygadzista Kilof", "Pani Drzemka", "Werbownik Hak",
        "Dozorca Bat", "Kadrowa Żyleta"};

    private FabrykaIstot() {
    }

    public static Potwor losowyPotwor(Random los, Set<String> zajete) {
        double rzut = los.nextDouble();
        if (rzut < 0.45) {
            return goblin(los, zajete);
        }
        return rzut < 0.80 ? ork(los, zajete) : ogr(los, zajete);
    }

    public static Goblin goblin(Random los, Set<String> zajete) {
        int sila = zakres(los, 6, 9);
        int zycie = zakres(los, 18, 26);
        return new Goblin(imie(GOBLINY, los, zajete), sila, zycie, zakres(los, 30, 45), zold(los, sila, zycie));
    }

    public static Ork ork(Random los, Set<String> zajete) {
        int sila = zakres(los, 12, 16);
        int zycie = zakres(los, 50, 65);
        return new Ork(imie(ORKI, los, zajete), sila, zycie, zakres(los, 60, 80), zold(los, sila, zycie));
    }

    public static Ogr ogr(Random los, Set<String> zajete) {
        int sila = zakres(los, 18, 24);
        int zycie = zakres(los, 90, 120);
        return new Ogr(imie(OGRY, los, zajete), sila, zycie, zakres(los, 80, 100), zold(los, sila, zycie));
    }

    public static Bohater losowyBohater(Random los, Set<String> zajete) {
        double rzut = los.nextDouble();
        if (rzut < 0.45) {
            return rycerz(los, zajete);
        }
        return rzut < 0.75 ? lucznik(los, zajete) : zlodziej(los, zajete);
    }

    public static Rycerz rycerz(Random los, Set<String> zajete) {
        return new Rycerz(imie(RYCERZE, los, zajete), zakres(los, 14, 18), zakres(los, 70, 90),
                zakres(los, 80, 100), zakres(los, 20, 40));
    }

    /** Niedoświadczony rycerz: przeciwnik na pierwszy atak w samouczku. */
    public static Rycerz giermek(Random los, Set<String> zajete) {
        return new Rycerz(imie(RYCERZE, los, zajete), zakres(los, 9, 11), zakres(los, 40, 50),
                zakres(los, 50, 60), zakres(los, 25, 35));
    }

    public static Lucznik lucznik(Random los, Set<String> zajete) {
        return new Lucznik(imie(LUCZNICY, los, zajete), zakres(los, 16, 20), zakres(los, 40, 55),
                zakres(los, 50, 70), zakres(los, 15, 30));
    }

    public static Zlodziej zlodziej(Random los, Set<String> zajete) {
        return new Zlodziej(imie(ZLODZIEJE, los, zajete), zakres(los, 35, 50), zakres(los, 30, 45), zakres(los, 5, 15));
    }

    public static MinisterFinansow ministerFinansow(Random los, boolean zachowawczy, Set<String> zajete) {
        String imie = imie(MINISTROWIE_FINANSOW, los, zajete);
        int negocjacjeProcent = zakres(los, 0, 25);
        int pensja = 6 + (int) Math.round(negocjacjeProcent * 0.8) + zakres(los, 0, 6);
        double negocjacje = negocjacjeProcent / 100.0;
        return zachowawczy
                ? new ZachowawczyMinisterFinansow(imie, pensja, negocjacje)
                : new RozrzutnyMinisterFinansow(imie, pensja, negocjacje);
    }

    public static MinisterPracy ministerPracy(Random los, boolean rambo, Set<String> zajete) {
        String imie = imie(MINISTROWIE_PRACY, los, zajete);
        int werbunek = zakres(los, 0, 2);
        int nadzorProcent = zakres(los, 0, 25);
        int pensja = 6 + 6 * werbunek + (int) Math.round(nadzorProcent * 0.5) + zakres(los, 0, 6);
        double nadzor = nadzorProcent / 100.0;
        return rambo
                ? new RamboMinisterPracy(imie, pensja, werbunek, nadzor)
                : new BylejakiMinisterPracy(imie, pensja, werbunek, nadzor);
    }

    /** Żołd rośnie razem z siłą i zdrowiem potwora. */
    private static int zold(Random los, int sila, int zycie) {
        return Math.max(1, (int) Math.round((sila + zycie) * 0.3) + zakres(los, -2, 2));
    }

    private static int zakres(Random los, int min, int max) {
        return min + los.nextInt(max - min + 1);
    }

    /** Losuje imię spośród jeszcze wolnych; numer dostaje tylko wtedy, gdy cała pula jest zajęta. */
    private static String imie(String[] pula, Random los, Set<String> zajete) {
        List<String> wolne = new ArrayList<>();
        for (String kandydat : pula) {
            if (!zajete.contains(kandydat)) {
                wolne.add(kandydat);
            }
        }
        String imie;
        if (wolne.isEmpty()) {
            String baza = pula[los.nextInt(pula.length)];
            imie = baza;
            for (int numer = 2; zajete.contains(imie); numer++) {
                imie = baza + " " + numer;
            }
        } else {
            imie = wolne.get(los.nextInt(wolne.size()));
        }
        zajete.add(imie);
        return imie;
    }
}
