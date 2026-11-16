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
            MinisterFinansow finansow = gra.getStartowiFinansow().stream()
                    .filter(m -> m instanceof RozrzutnyMinisterFinansow)
                    .min(Comparator.comparingInt(MinisterFinansow::getPensja))
                    .orElse(gra.getStartowiFinansow().get(0));
            MinisterPracy pracy = gra.getStartowiPracy().stream()
                    .filter(m -> m instanceof RamboMinisterPracy)
                    .min(Comparator.comparingInt(MinisterPracy::getPensja))
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

            for (Ulepszenie u : new Ulepszenie[] {Ulepszenie.PULAPKI, Ulepszenie.KOPALNIA, Ulepszenie.PULAPKI,
                    Ulepszenie.KOPALNIA, Ulepszenie.PULAPKI, Ulepszenie.KOPALNIA, Ulepszenie.ZAMEK_SKARBCA}) {
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

    @Test
    void dobreDecyzjeGrajaDlugoALosoweSzybkoPrzegrywaja() {
        int[] losowy = wyniki(LOSOWY);
        int[] rozsadny = wyniki(ROZSADNY);
        System.out.println("BALANS " + opis("losowy", losowy));
        System.out.println("BALANS " + opis("rozsądny", rozsadny));
        int medianaLosowego = losowy[losowy.length / 2];
        int medianaRozsadnego = rozsadny[rozsadny.length / 2];
        assertTrue(medianaLosowego < 60, "losowe decyzje szybko gubią loch");
        assertTrue(medianaRozsadnego > 2 * medianaLosowego, "rozsądne decyzje dają dużo dłuższą grę");
        long bezKonca = Arrays.stream(rozsadny).filter(d -> d > MAKS_DNI).count();
        assertTrue(bezKonca >= GIER / 10, "przy dobrym losie rozsądny loch trwa bardzo długo");
        assertTrue(bezKonca <= GIER / 2, "ale nie jest to pewne: pech i fale ataków potrafią go zniszczyć");
    }
}
