package wladcalochow.silnik;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import wladcalochow.model.Loch;
import wladcalochow.model.MinisterFinansow;
import wladcalochow.model.MinisterPracy;
import wladcalochow.model.Potwor;
import wladcalochow.model.RozrzutnyMinisterFinansow;
import wladcalochow.model.RamboMinisterPracy;
import wladcalochow.model.Walka;
import wladcalochow.model.strategia.Postawa;
import wladcalochow.model.strategia.RaportZwiadu;
import wladcalochow.model.strategia.Rozkaz;
import wladcalochow.model.strategia.Ulepszenie;

/**
 * Pomiar balansu: setki gier rozgrywanych przez dwa automaty. Losowy gracz ma przegrywać szybko,
 * rozsądny (korzysta tylko z tego, co widzi gracz: zwiadu, kosztów, stanu lochu) ma czasem grać
 * bardzo długo. Gra nie ma końca, więc „długo” liczymy w dniach.
 */
class BalansSymulacjaTest {

    static final int GIER = 300;
    static final int MAKS_DNI = 200;

    interface Gracz {
        void start(Gra gra);

        void poranek(Gra gra, Random bot);
    }

    /** Losowe, dozwolone rozkazy. */
    static final Gracz LOSOWY = new Gracz() {
        @Override
        public void start(Gra gra) {
            gra.rozpocznij(gra.getStartowiFinansow().get(0), gra.getStartowiPracy().get(0));
        }

        @Override
        public void poranek(Gra gra, Random bot) {
            Loch loch = gra.getLoch();
            for (int proba = 0; proba < 4 && gra.getDzien().maRozkazy(); proba++) {
                List<Rozkaz> pomysly = new ArrayList<>();
                pomysly.add(new Rozkaz.ZmianaPostawy(Postawa.values()[bot.nextInt(Postawa.values().length)]));
                pomysly.add(new Rozkaz.LimitRekrutacji(25 * bot.nextInt(5)));
                pomysly.add(new Rozkaz.ZakupUlepszenia(Ulepszenie.values()[bot.nextInt(Ulepszenie.values().length)]));
                if (!loch.getListaPotworow().isEmpty()) {
                    pomysly.add(new Rozkaz.Premia(loch.getListaPotworow().get(bot.nextInt(loch.getListaPotworow().size()))));
                }
                Rozkaz rozkaz = pomysly.get(bot.nextInt(pomysly.size()));
                if (bot.nextBoolean() && gra.powodOdmowy(rozkaz) == null) {
                    gra.wykonajRozkaz(rozkaz);
                }
            }
        }
    };

    /** Prosta, rozsądna strategia: rezerwa na wydatki dnia, ulepszenia w kolejności, łapówka przy przewadze wroga. */
    static final Gracz ROZSADNY = new Gracz() {
        @Override
        public void start(Gra gra) {
            // Na starcie czyta cechy i wady (unika plotkarzy i nielojalnych), potem już ministrów nie zmienia.
            MinisterFinansow finansow = gra.getStartowiFinansow().stream()
                    .filter(m -> m instanceof RozrzutnyMinisterFinansow)
                    .max(Comparator.comparingInt(m -> ocena(m.getCecha(), m.getWada())))
                    .orElse(gra.getStartowiFinansow().get(0));
            MinisterPracy pracy = gra.getStartowiPracy().stream()
                    .filter(m -> m instanceof RamboMinisterPracy)
                    .max(Comparator.comparingInt(m -> ocena(m.getCecha(), m.getWada())))
                    .orElse(gra.getStartowiPracy().get(0));
            gra.rozpocznij(finansow, pracy);
        }

        @Override
        public void poranek(Gra gra, Random bot) {
            Loch loch = gra.getLoch();
            int zloto = loch.getSkarbiec().getZloto();
            int rezerwa = loch.getMinisterFinansow().getPensja() + loch.getMinisterPracy().getPensja()
                    + loch.dziennyKosztZoldu() * 2 / 3;
            RaportZwiadu raport = gra.getDzien().getRaport();
            int wojownicy = loch.getListaPotworow().size() - loch.getListaPotworow().size() / 3;

            boolean lapowka = raport.maxBohaterow() > 0 && raport.szacowanaSila() <= zloto - rezerwa / 2
                    && raport.maxBohaterow() >= wojownicy;
            Postawa postawa = lapowka ? Postawa.PRZEKUPSTWO : Postawa.PELNA_OBRONA;
            wydaj(gra, new Rozkaz.ZmianaPostawy(postawa));
            if (lapowka) {
                zloto -= raport.szacowanaSila();
            }

            // Na przemian pułapki i kopalnia do końca, zamek na koniec.
            for (Ulepszenie u : new Ulepszenie[] {Ulepszenie.PULAPKI, Ulepszenie.KOPALNIA, Ulepszenie.ZAMEK_SKARBCA}) {
                Rozkaz zakup = new Rozkaz.ZakupUlepszenia(u);
                if (gra.powodOdmowy(zakup) == null && gra.koszt(zakup) <= zloto - rezerwa) {
                    wydaj(gra, zakup);
                    zloto -= gra.koszt(zakup);
                    break;
                }
            }
            wydaj(gra, new Rozkaz.LimitRekrutacji(100));
            for (Potwor p : loch.getListaPotworow()) {
                Rozkaz premia = new Rozkaz.Premia(p);
                if (p.procentMorale() < 0.5 && gra.powodOdmowy(premia) == null && gra.koszt(premia) <= zloto - rezerwa) {
                    wydaj(gra, premia);
                    break;
                }
            }
        }

        private void wydaj(Gra gra, Rozkaz rozkaz) {
            if (gra.getDzien().maRozkazy() && gra.powodOdmowy(rozkaz) == null) {
                gra.wykonajRozkaz(rozkaz);
            }
        }
    };

    /**
     * Strategia subagenta, który przetrwał 130+ dni: ulepszenia szybko i do końca, silne potwory w obronie
     * (słabe zwalniane, gdy w kolejce czeka ktoś silniejszy), łapówka przy wyrównanym ataku, odwód przy
     * słabym ataku (chroni weteranów). Korzysta tylko z tego, co widzi gracz: zwiadu i statystyk własnych potworów.
     */
    /** Ocena cechy i wady ministra z punktu widzenia doświadczonego gracza (bez liczenia przyszłości). */
    static int ocena(wladcalochow.model.strategia.CechaMinistra cecha, wladcalochow.model.strategia.WadaMinistra wada) {
        int punkty = cecha == null ? 0 : switch (cecha) {
            case NEGOCJATOR, MEDYK -> 3;
            case NADZORCA, LICHWIARZ -> 2;
            case TRENER, WERBOWNIK -> 1;
        };
        return punkty - (wada == null ? 0 : switch (wada) {
            case PLOTKARZ -> 3;
            case NIELOJALNY -> 2;
            case CHCIWY, NIESLOWNY -> 1;
        });
    }

    static Gracz ekspert(boolean zOdwodem) {
        return new Gracz() {
            @Override
            public void start(Gra gra) {
                MinisterFinansow finansow = gra.getStartowiFinansow().stream()
                        .filter(m -> m instanceof RozrzutnyMinisterFinansow)
                        .max(Comparator.comparingInt(m -> ocena(m.getCecha(), m.getWada())))
                        .orElse(gra.getStartowiFinansow().get(0));
                MinisterPracy pracy = gra.getStartowiPracy().stream()
                        .filter(m -> m instanceof RamboMinisterPracy)
                        .max(Comparator.comparingInt(m -> ocena(m.getCecha(), m.getWada())))
                        .orElse(gra.getStartowiPracy().get(0));
                gra.rozpocznij(finansow, pracy);
            }

            /** Wymiana ministra, gdy kandydat tego samego stylu ma wyraźnie lepszą cechę i wadę. */
            private void wymienMinistra(Gra gra, int wolneZloto) {
                Loch loch = gra.getLoch();
                MinisterFinansow mf = loch.getMinisterFinansow();
                for (MinisterFinansow k : gra.getDzien().getKandydaciFinansow()) {
                    Rozkaz r = new Rozkaz.ZmianaMinistraFinansow(k);
                    if (k instanceof RozrzutnyMinisterFinansow && gra.getNastepnyMinisterFinansow() == null
                            && ocena(k.getCecha(), k.getWada()) >= ocena(mf.getCecha(), mf.getWada()) + 2
                            && gra.koszt(r) <= wolneZloto) {
                        wydaj(gra, r);
                        return;
                    }
                }
                MinisterPracy mp = loch.getMinisterPracy();
                for (MinisterPracy k : gra.getDzien().getKandydaciPracy()) {
                    Rozkaz r = new Rozkaz.ZmianaMinistraPracy(k);
                    if (k instanceof RamboMinisterPracy && gra.getNastepnyMinisterPracy() == null
                            && ocena(k.getCecha(), k.getWada()) >= ocena(mp.getCecha(), mp.getWada()) + 2
                            && gra.koszt(r) <= wolneZloto) {
                        wydaj(gra, r);
                        return;
                    }
                }
            }

            @Override
            public void poranek(Gra gra, Random bot) {
                Loch loch = gra.getLoch();
                int zloto = loch.getSkarbiec().getZloto();
                int pensje = loch.getMinisterFinansow().getPensja() + loch.getMinisterPracy().getPensja();
                int rezerwa = pensje + loch.dziennyKosztZoldu() * 2 / 3;
                RaportZwiadu raport = gra.getDzien().getRaport();
                List<Potwor> potwory = loch.getListaPotworow();
                int obrona = wladcalochow.model.DzienGry.wartoscOddzialu(potwory) * 2 / 3;

                Postawa postawa = Postawa.PELNA_OBRONA;
                if (raport.maxBohaterow() > 0) {
                    boolean groznie = raport.szacowanaSila() >= obrona * 0.8;
                    if (groznie && raport.szacowanaSila() <= zloto - pensje) {
                        postawa = Postawa.PRZEKUPSTWO;
                        zloto -= raport.szacowanaSila();
                    } else if (zOdwodem && raport.szacowanaSila() < obrona * 0.8) {
                        postawa = Postawa.ODWOD;
                    }
                }
                wydaj(gra, new Rozkaz.ZmianaPostawy(postawa));

                // Najtańszy dostępny poziom spośród kopalni i pułapek, potem zamek.
                List<Ulepszenie> kolejnosc = new ArrayList<>(List.of(Ulepszenie.KOPALNIA, Ulepszenie.PULAPKI));
                kolejnosc.sort(Comparator.comparingInt(u -> loch.poziom(u)));
                kolejnosc.add(Ulepszenie.ZAMEK_SKARBCA);
                for (Ulepszenie u : kolejnosc) {
                    Rozkaz zakup = new Rozkaz.ZakupUlepszenia(u);
                    if (gra.powodOdmowy(zakup) == null && gra.koszt(zakup) <= zloto - pensje) {
                        wydaj(gra, zakup);
                        zloto -= gra.koszt(zakup);
                        break;
                    }
                }
                wydaj(gra, new Rozkaz.LimitRekrutacji(100));
                wymienMinistra(gra, zloto - rezerwa);
                // Miejsce dla silniejszych: gdy loch prawie pełny, a w kolejce stoi ktoś mocniejszy od najsłabszego.
                int najlepszyKandydat = gra.getDzien().getKandydaci().stream().mapToInt(Potwor::moc).max().orElse(0);
                Potwor najslabszy = potwory.stream().min(Comparator.comparingInt(Potwor::moc)).orElse(null);
                if (najslabszy != null && potwory.size() >= Loch.POJEMNOSC - 1 && najlepszyKandydat > najslabszy.moc() * 2) {
                    wydaj(gra, new Rozkaz.Zwolnienie(najslabszy));
                }
                // Szpital dla najsilniejszego rannego, gdy atak jest zapowiedziany, a złota starcza ponad zapas.
                if (raport.maxBohaterow() > 0) {
                    potwory.stream().filter(Potwor::czyRanny).max(Comparator.comparingInt(Potwor::moc)).ifPresent(r -> {
                        Rozkaz szpital = new Rozkaz.Szpital(r);
                        if (gra.powodOdmowy(szpital) == null && gra.koszt(szpital) <= loch.getSkarbiec().getZloto() - rezerwa) {
                            wydaj(gra, szpital);
                        }
                    });
                }
                for (Potwor p : potwory) {
                    Rozkaz premia = new Rozkaz.Premia(p);
                    if (p.procentMorale() < 0.5 && gra.powodOdmowy(premia) == null && gra.koszt(premia) <= zloto - rezerwa) {
                        wydaj(gra, premia);
                        break;
                    }
                }
            }

            private void wydaj(Gra gra, Rozkaz rozkaz) {
                if (gra.getDzien().maRozkazy() && gra.powodOdmowy(rozkaz) == null) {
                    gra.wykonajRozkaz(rozkaz);
                }
            }
        };
    }

    /** Nie wydaje żadnego rozkazu przez całą grę (ministrów bierze pierwszych z brzegu). */
    static final Gracz BIERNY = new Gracz() {
        @Override
        public void start(Gra gra) {
            gra.rozpocznij(gra.getStartowiFinansow().get(0), gra.getStartowiPracy().get(0));
        }

        @Override
        public void poranek(Gra gra, Random bot) {
        }
    };

    /** Gra jak ekspert przez pierwsze dni, a potem przestaje cokolwiek robić. */
    static Gracz tylkoPoczatek(int dni) {
        Gracz ekspert = ekspert(true);
        return new Gracz() {
            @Override
            public void start(Gra gra) {
                ekspert.start(gra);
            }

            @Override
            public void poranek(Gra gra, Random bot) {
                if (gra.getNumerDnia() <= dni) {
                    ekspert.poranek(gra, bot);
                }
            }
        };
    }

    /** Wynik jednej gry: ile dni przetrwał loch i ile z nich minęło bez żadnego rozkazu. */
    record Przebieg(int dni, int dniBezRozkazow, int dniZOdwodem) {
    }

    static Przebieg przebieg(Gracz gracz, long ziarno, int maksDni) {
        Gra gra = Gra.nowa(ziarno);
        gracz.start(gra);
        Random bot = new Random(ziarno * 7919);
        int bezRozkazow = 0;
        int zOdwodem = 0;
        while (!gra.czyKoniecGry() && gra.getNumerDnia() < maksDni) {
            int limit = gra.getDzien().getPozostaleRozkazy();
            gracz.poranek(gra, bot);
            // Rozkazy bez zmiany (np. ta sama postawa) gra odrzuca, więc liczą się tylko prawdziwe decyzje.
            if (gra.getDzien().getPozostaleRozkazy() == limit) {
                bezRozkazow++;
            }
            if (gra.getLoch().getPostawa() == Postawa.ODWOD && gra.getDzien().getRaport().maxBohaterow() > 0) {
                zOdwodem++;
            }
            gra.rozegrajPoranek();
            Walka walka = gra.rozpocznijObrone();
            while (walka != null && !walka.czyZakonczona()) {
                gra.krokWalki();
            }
            gra.zakonczDzien();
            if (!gra.czyKoniecGry()) {
                gra.rozpocznijDzien();
            }
        }
        return new Przebieg(gra.czyKoniecGry() ? gra.getNumerDnia() : maksDni + 1, bezRozkazow, zOdwodem);
    }

    static int rozegraj(Gracz gracz, long ziarno) {
        Gra gra = Gra.nowa(ziarno);
        gracz.start(gra);
        Random bot = new Random(ziarno * 7919);
        while (!gra.czyKoniecGry() && gra.getNumerDnia() < MAKS_DNI) {
            gracz.poranek(gra, bot);
            gra.rozegrajPoranek();
            Walka walka = gra.rozpocznijObrone();
            while (walka != null && !walka.czyZakonczona()) {
                gra.krokWalki();
            }
            gra.zakonczDzien();
            if (!gra.czyKoniecGry()) {
                gra.rozpocznijDzien();
            }
        }
        return gra.czyKoniecGry() ? gra.getNumerDnia() : MAKS_DNI + 1;
    }

    static int[] wyniki(Gracz gracz) {
        int[] dni = new int[GIER];
        for (int i = 0; i < GIER; i++) {
            dni[i] = rozegraj(gracz, 1000 + i);
        }
        Arrays.sort(dni);
        return dni;
    }

    static String opis(String nazwa, int[] dni) {
        long ponad100 = Arrays.stream(dni).filter(d -> d > 100).count();
        long bezKonca = Arrays.stream(dni).filter(d -> d > MAKS_DNI).count();
        return String.format("%-9s mediana %3d · 10%% %3d · 90%% %3d · ponad 100 dni: %3d%% · ponad %d dni: %3d%%",
                nazwa, dni[dni.length / 2], dni[dni.length / 10], dni[dni.length * 9 / 10],
                100 * ponad100 / dni.length, MAKS_DNI, 100 * bezKonca / dni.length);
    }

    static final int MAKS_DNI_EKSPERTA = 1000;

    static String opisEksperta(String nazwa, Gracz gracz) {
        List<Przebieg> gry = new ArrayList<>();
        for (int i = 0; i < GIER; i++) {
            gry.add(przebieg(gracz, 1000 + i, MAKS_DNI_EKSPERTA));
        }
        int[] dni = gry.stream().mapToInt(Przebieg::dni).sorted().toArray();
        long sumaDni = gry.stream().mapToLong(p -> Math.min(p.dni(), MAKS_DNI_EKSPERTA)).sum();
        long bez = gry.stream().mapToLong(Przebieg::dniBezRozkazow).sum();
        long odwod = gry.stream().mapToLong(Przebieg::dniZOdwodem).sum();
        return String.format("%-12s mediana %4d · 10%% %4d · 90%% %4d · >300: %3d%% · >500: %3d%% · >%d: %3d%%"
                        + " · dni bez rozkazów %2d%% · odwód %2d%%",
                nazwa, dni[GIER / 2], dni[GIER / 10], dni[GIER * 9 / 10],
                100 * Arrays.stream(dni).filter(d -> d > 300).count() / GIER,
                100 * Arrays.stream(dni).filter(d -> d > 500).count() / GIER, MAKS_DNI_EKSPERTA,
                100 * Arrays.stream(dni).filter(d -> d > MAKS_DNI_EKSPERTA).count() / GIER,
                100 * bez / sumaDni, 100 * odwod / sumaDni);
    }

    @Test
    void ekspertGraBardzoDlugoAleNieBezKonca() {
        System.out.println("BALANS " + opisEksperta("ekspert", ekspert(true)));
        System.out.println("BALANS " + opisEksperta("bez odwodu", ekspert(false)));
        System.out.println("BALANS " + opisEksperta("rozsądny", ROZSADNY));
        int[] dni = new int[GIER];
        for (int i = 0; i < GIER; i++) {
            dni[i] = przebieg(ekspert(true), 1000 + i, MAKS_DNI_EKSPERTA).dni();
        }
        Arrays.sort(dni);
        int mediana = dni[GIER / 2];
        assertTrue(mediana >= 150 && mediana <= 350, "dobra gra trwa długo, ale ma koniec: mediana " + mediana);
        long ponad300 = Arrays.stream(dni).filter(d -> d > 300).count();
        long ponad500 = Arrays.stream(dni).filter(d -> d > 500).count();
        assertTrue(ponad300 >= GIER / 20, "z dobrym losem da się zajść daleko");
        assertTrue(ponad500 <= GIER / 7, "ale mało kto przetrwa 500 dni");

        int[] bezOdwodu = new int[GIER];
        for (int i = 0; i < GIER; i++) {
            bezOdwodu[i] = przebieg(ekspert(false), 1000 + i, MAKS_DNI_EKSPERTA).dni();
        }
        Arrays.sort(bezOdwodu);
        assertTrue(mediana > bezOdwodu[GIER / 2] * 1.05, "odwód używany z głową wydłuża grę: z odwodem " + mediana
                + ", bez " + bezOdwodu[GIER / 2]);
    }

    @Test
    void biernyGraczISamPoczatekSzybkoPrzegrywaja() {
        System.out.println("BALANS " + opisEksperta("bierny", BIERNY));
        System.out.println("BALANS " + opisEksperta("poczatek 20", tylkoPoczatek(20)));
        System.out.println("BALANS " + opisEksperta("poczatek 40", tylkoPoczatek(40)));
        int bierny = mediana(BIERNY);
        int poczatek20 = mediana(tylkoPoczatek(20));
        int ekspert = mediana(ekspert(true));
        int losowy = mediana(LOSOWY);
        int rozsadny = mediana(ROZSADNY);
        assertTrue(bierny <= losowy * 1.05, "kto nic nie robi, nie gra lepiej niż losowo: bierny " + bierny
                + ", losowy " + losowy);
        assertTrue(poczatek20 < 60, "kto zadbał tylko o początek, też szybko przegrywa: mediana " + poczatek20);
        assertTrue(rozsadny >= 1.6 * bierny, "przeciętna gra wyraźnie dłuższa niż bierna: przeciętny " + rozsadny
                + ", bierny " + bierny);
        assertTrue(ekspert > 4 * poczatek20, "długa gra wymaga decyzji przez całą grę: ekspert " + ekspert
                + ", tylko początek " + poczatek20);
    }

    /** Ekspert, który część dni pomija (nic nie robi), a część gra losowo (złe decyzje). */
    static Gracz zBledami(Gracz baza, double pomin, double losowo) {
        return new Gracz() {
            @Override
            public void start(Gra gra) {
                baza.start(gra);
            }

            @Override
            public void poranek(Gra gra, Random bot) {
                double r = bot.nextDouble();
                if (r < pomin) {
                    return;
                }
                if (r < pomin + losowo) {
                    LOSOWY.poranek(gra, bot);
                    return;
                }
                baza.poranek(gra, bot);
            }
        };
    }

    /** Ekspert z innymi stylami ministrów na starcie (najlepsza para cecha-wada w danym stylu). */
    static Gracz ekspertZMinistrami(Class<?> finanse, Class<?> praca) {
        Gracz ekspert = ekspert(true);
        return new Gracz() {
            @Override
            public void start(Gra gra) {
                MinisterFinansow f = gra.getStartowiFinansow().stream().filter(finanse::isInstance)
                        .max(Comparator.comparingInt(m -> ocena(m.getCecha(), m.getWada())))
                        .orElse(gra.getStartowiFinansow().get(0));
                MinisterPracy m = gra.getStartowiPracy().stream().filter(praca::isInstance)
                        .max(Comparator.comparingInt(x -> ocena(x.getCecha(), x.getWada())))
                        .orElse(gra.getStartowiPracy().get(0));
                gra.rozpocznij(f, m);
            }

            @Override
            public void poranek(Gra gra, Random bot) {
                ekspert.poranek(gra, bot);
            }
        };
    }

    /**
     * Gra ma wybaczać: nie trzeba grać jak robot. Kto pomija co czwarty dzień albo co czwarty dzień
     * decyduje losowo, nadal gra kilka razy dłużej niż ktoś, kto nic nie robi.
     */
    @Test
    void graWybaczaPominieteDniIZleDecyzje() {
        int bierny = mediana(BIERNY);
        int pomija = mediana(zBledami(ekspert(true), 0.25, 0));
        int losowo = mediana(zBledami(ekspert(true), 0, 0.25));
        System.out.println("BALANS błędy: bierny " + bierny + " · pomija 25% dni " + pomija + " · losowo 25% dni " + losowo);
        assertTrue(pomija > 3 * bierny, "pominięte dni nie przekreślają gry: " + pomija + " vs bierny " + bierny);
        assertTrue(losowo > 3 * bierny, "złe decyzje nie przekreślają gry: " + losowo + " vs bierny " + bierny);
    }

    /** Wiele dróg do sukcesu: każda para stylów ministrów pozwala grać długo (nie ma jednej słusznej). */
    @Test
    void kazdaParaMinistrowPozwalaGracDlugo() {
        int ekspert = mediana(ekspert(true));
        Class<?>[] finanse = {RozrzutnyMinisterFinansow.class, wladcalochow.model.ZachowawczyMinisterFinansow.class};
        Class<?>[] praca = {RamboMinisterPracy.class, wladcalochow.model.BylejakiMinisterPracy.class};
        for (Class<?> f : finanse) {
            for (Class<?> m : praca) {
                int wynik = mediana(ekspertZMinistrami(f, m));
                System.out.println("BALANS ministrowie " + f.getSimpleName() + " + " + m.getSimpleName() + ": " + wynik);
                assertTrue(wynik > ekspert * 3 / 4, f.getSimpleName() + " + " + m.getSimpleName() + " daje tylko "
                        + wynik + " dni przy " + ekspert + " u eksperta");
            }
        }
    }

    /*
     * Człowiek z podpowiedziami: nic nie liczy, czyta tylko to, co gra pokazuje słowami i znakami.
     * Ocena ataku decyduje o postawie (groźny: łapówka, słabszy: odwód), kupuje wszystko, na co go stać,
     * ministrów wybiera po cesze i wadzie i ich nie zmienia, a przy pełnym lochu robi miejsce dla silniejszych.
     *
     * Przełączniki to warianty zmierzone 18.11.2026 (mediana dni; ekspert 334, bierny 42):
     * zapas na pensje i żołd 87, zapas na pensje 98, bez zapasu 153 (wydane złoto ściąga słabsze ataki);
     * rezerwa na łapówkę 118; wymiana ministrów 129; łapówka przy „wyrównanym” bez zysku;
     * zwalnianie najsłabszego dla 2× silniejszego kandydata 214. Domyślne ustawienia to najlepszy wariant.
     */
    static int ZAPAS = 2;
    static boolean LAP_WYROWNANY = false;
    static boolean ROZRZUTNY = false;
    static boolean WYROWNANY_ODWOD = false;
    static boolean WYMIANA = false;
    static boolean ZWALNIA = true;

    static final Gracz Z_PODPOWIEDZIAMI = new Gracz() {
        @Override
        public void start(Gra gra) {
            MinisterFinansow f = gra.getStartowiFinansow().stream()
                    .filter(x -> !ROZRZUTNY || x instanceof RozrzutnyMinisterFinansow)
                    .max(Comparator.comparingInt(m -> ocena(m.getCecha(), m.getWada())))
                    .orElse(gra.getStartowiFinansow().get(0));
            MinisterPracy m = gra.getStartowiPracy().stream()
                    .max(Comparator.comparingInt(x -> ocena(x.getCecha(), x.getWada())))
                    .orElse(gra.getStartowiPracy().get(0));
            gra.rozpocznij(f, m);
        }

        private boolean starczy(Gra gra, Rozkaz r) {
            if (gra.powodOdmowy(r) != null) {
                return false;
            }
            if (ZAPAS == 3) {
                // Kupuje wszystko, co nie ma ✖ (żółte „!” przy żołdzie akceptuje).
                return gra.brakNaKonieczne(gra.koszt(r)) == 0;
            }
            int pensje = gra.kosztyDnia() - gra.getLoch().dziennyKosztZoldu();
            int zapas = ZAPAS == 0 ? gra.kosztyDnia() : ZAPAS == 1 ? pensje : 0;
            return gra.getLoch().getSkarbiec().getZloto() - gra.koszt(r) >= zapas;
        }

        private void wydaj(Gra gra, Rozkaz r) {
            if (gra.getDzien().maRozkazy() && gra.powodOdmowy(r) == null) {
                gra.wykonajRozkaz(r);
            }
        }

        @Override
        public void poranek(Gra gra, Random bot) {
            Loch loch = gra.getLoch();
            wladcalochow.model.strategia.OcenaAtaku ocena = gra.ocenaAtaku();
            int lapowka = gra.getDzien().getRaport().szacowanaSila();
            Postawa postawa = switch (ocena) {
                case GROZNY -> gra.getLoch().getSkarbiec().getZloto() >= lapowka ? Postawa.PRZEKUPSTWO : Postawa.PELNA_OBRONA;
                case WYROWNANY -> LAP_WYROWNANY && gra.getLoch().getSkarbiec().getZloto() >= lapowka
                        ? Postawa.PRZEKUPSTWO : WYROWNANY_ODWOD ? Postawa.ODWOD : Postawa.PELNA_OBRONA;
                case SLABSZY -> Postawa.ODWOD;
                default -> Postawa.PELNA_OBRONA;
            };
            wydaj(gra, new Rozkaz.ZmianaPostawy(postawa));
            wydaj(gra, new Rozkaz.LimitRekrutacji(100));
            // Wymiana ministra, gdy w tabeli stoi wyraźnie lepszy (cecha i wada widać w oknie) i jest ✔.
            if (WYMIANA) {
                MinisterFinansow mf = loch.getMinisterFinansow();
                for (MinisterFinansow k : gra.getDzien().getKandydaciFinansow()) {
                    Rozkaz r = new Rozkaz.ZmianaMinistraFinansow(k);
                    if (gra.getNastepnyMinisterFinansow() == null
                            && ocena(k.getCecha(), k.getWada()) >= ocena(mf.getCecha(), mf.getWada()) + 2 && starczy(gra, r)) {
                        wydaj(gra, r);
                        break;
                    }
                }
                MinisterPracy mp = loch.getMinisterPracy();
                for (MinisterPracy k : gra.getDzien().getKandydaciPracy()) {
                    Rozkaz r = new Rozkaz.ZmianaMinistraPracy(k);
                    if (gra.getNastepnyMinisterPracy() == null
                            && ocena(k.getCecha(), k.getWada()) >= ocena(mp.getCecha(), mp.getWada()) + 2 && starczy(gra, r)) {
                        wydaj(gra, r);
                        break;
                    }
                }
            }
            // Miejsce dla silniejszych: pełny loch, a w kolejce stoi ktoś wyraźnie mocniejszy od najsłabszego.
            if (ZWALNIA) {
                int najlepszy = gra.getDzien().getKandydaci().stream().mapToInt(Potwor::moc).max().orElse(0);
                Potwor najslabszy = loch.getListaPotworow().stream().min(Comparator.comparingInt(Potwor::moc)).orElse(null);
                if (najslabszy != null && loch.getListaPotworow().size() >= Loch.POJEMNOSC - 1 && najlepszy > najslabszy.moc() * 2) {
                    wydaj(gra, new Rozkaz.Zwolnienie(najslabszy));
                }
            }
            // Szpital dla rannego, gdy coś nadciąga i jest ✔.
            if (ocena != wladcalochow.model.strategia.OcenaAtaku.SPOKOJ) {
                for (Potwor p : loch.getListaPotworow()) {
                    Rozkaz s = new Rozkaz.Szpital(p);
                    if (p.czyRanny() && starczy(gra, s)) {
                        wydaj(gra, s);
                        break;
                    }
                }
            }
            // Ulepszenia na zmianę, tylko z ✔.
            List<Ulepszenie> kolejnosc = new ArrayList<>(List.of(Ulepszenie.KOPALNIA, Ulepszenie.PULAPKI));
            kolejnosc.sort(Comparator.comparingInt(loch::poziom));
            kolejnosc.add(Ulepszenie.ZAMEK_SKARBCA);
            for (Ulepszenie u : kolejnosc) {
                Rozkaz r = new Rozkaz.ZakupUlepszenia(u);
                if (starczy(gra, r)) {
                    wydaj(gra, r);
                    break;
                }
            }
            for (Potwor p : loch.getListaPotworow()) {
                Rozkaz r = new Rozkaz.Premia(p);
                if (p.procentMorale() < 0.5 && starczy(gra, r)) {
                    wydaj(gra, r);
                    break;
                }
            }
        }
    };

    /** Podpowiedzi zastępują kalkulator: gracz, który tylko je czyta, gra kilka razy dłużej niż bierny. */
    @Test
    void podpowiedziZastepujaLiczenie() {
        int ekspert = mediana(ekspert(true));
        int czlowiek = mediana(Z_PODPOWIEDZIAMI);
        int bierny = mediana(BIERNY);
        System.out.println("BALANS podpowiedzi: ekspert " + ekspert + " · z podpowiedziami " + czlowiek
                + " · bierny " + bierny);
        System.out.println("BALANS " + opisEksperta("podpowiedzi", Z_PODPOWIEDZIAMI));
        assertTrue(czlowiek > 3 * bierny, "podpowiedzi wystarczają do długiej gry: " + czlowiek);
    }

    static int mediana(Gracz gracz) {
        int[] dni = new int[GIER];
        for (int i = 0; i < GIER; i++) {
            dni[i] = przebieg(gracz, 1000 + i, MAKS_DNI_EKSPERTA).dni();
        }
        Arrays.sort(dni);
        return dni[GIER / 2];
    }

    @Test
    void dobreDecyzjeGrajaDlugoALosoweSzybkoPrzegrywaja() {
        int[] losowy = wyniki(LOSOWY);
        int[] rozsadny = wyniki(ROZSADNY);
        System.out.println("BALANS " + opis("losowy", losowy));
        System.out.println("BALANS " + opis("rozsądny", rozsadny));
        int medianaLosowego = losowy[losowy.length / 2];
        int medianaRozsadnego = rozsadny[rozsadny.length / 2];
        assertTrue(medianaLosowego < 60, "losowe decyzje szybko gubią loch");
        assertTrue(medianaRozsadnego > medianaLosowego, "rozsądne decyzje dają dłuższą grę niż losowe");
    }
}
