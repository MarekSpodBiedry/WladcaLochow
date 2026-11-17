package wladcalochow.silnik;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import wladcalochow.model.Bohater;
import wladcalochow.model.DzienGry;
import wladcalochow.model.Istota;
import wladcalochow.model.Potwor;
import wladcalochow.model.Skarbiec;
import wladcalochow.model.Walka;

/**
 * Ile naprawdę jest wart w walce każdy rodzaj istoty. Oddział czterech istot jednego rodzaju walczy
 * z coraz liczniejszym, losowo złożonym oddziałem przeciwników. Wartość przeciwników przy 50% wygranych,
 * podzielona przez wartość oddziału ze wzoru, mówi, czy wzór dany rodzaj zawyża, czy zaniża.
 * Po wyrównaniu wzoru ten stosunek ma być podobny dla wszystkich rodzajów.
 */
class PojedynkiTest {

    private static final int WALK = 300;
    private static final int ODDZIAL = 4;

    private static boolean wygranaObrony(List<Potwor> obroncy, List<Bohater> bohaterowie, Random los) {
        Walka walka = new Walka(bohaterowie, obroncy, List.of(), new Skarbiec(1000), 1.0, los);
        while (!walka.czyZakonczona()) {
            walka.krok();
        }
        return walka.getWynik() == Walka.Wynik.OBRONA_UDANA;
    }

    /** Wartość przeciwników, przy której oddział wygrywa w połowie walk, podzielona przez wartość oddziału. */
    private static <A extends Istota, B extends Istota> double stosunek(BiFunction<Random, Set<String>, A> nasz,
            BiFunction<Random, Set<String>, B> wrog, boolean naszBroni) {
        Random los = new Random(42);
        double sumaNaszych = 0;
        double[] wartoscWrogow = new double[25];
        int[] wygrane = new int[25];
        for (int i = 0; i < WALK; i++) {
            for (int k = 1; k < wartoscWrogow.length; k++) {
                Set<String> imiona = new HashSet<>();
                List<A> nasi = new ArrayList<>();
                for (int j = 0; j < ODDZIAL; j++) {
                    nasi.add(nasz.apply(los, imiona));
                }
                List<B> wrogowie = new ArrayList<>();
                for (int j = 0; j < k; j++) {
                    wrogowie.add(wrog.apply(los, imiona));
                }
                if (k == 1) {
                    sumaNaszych += DzienGry.wartoscOddzialu(nasi);
                }
                wartoscWrogow[k] += DzienGry.wartoscOddzialu(wrogowie);
                @SuppressWarnings("unchecked")
                boolean wygrana = naszBroni
                        ? wygranaObrony((List<Potwor>) nasi, (List<Bohater>) wrogowie, los)
                        : !wygranaObrony((List<Potwor>) wrogowie, (List<Bohater>) nasi, los);
                if (wygrana) {
                    wygrane[k]++;
                }
            }
        }
        double naszych = sumaNaszych / WALK;
        for (int k = 1; k < wygrane.length; k++) {
            if (wygrane[k] * 2 < WALK) {
                // Interpolacja między k-1 a k.
                double przed = k == 1 ? 1.0 : wygrane[k - 1] / (double) WALK;
                double po = wygrane[k] / (double) WALK;
                double wartPrzed = k == 1 ? 0 : wartoscWrogow[k - 1] / WALK;
                double wartPo = wartoscWrogow[k] / WALK;
                double t = (przed - 0.5) / Math.max(1e-9, przed - po);
                return (wartPrzed + t * (wartPo - wartPrzed)) / naszych;
            }
        }
        return wartoscWrogow[wartoscWrogow.length - 1] / WALK / naszych;
    }

    static String raport() {
        StringBuilder s = new StringBuilder();
        s.append(String.format("POJEDYNKI goblin   %.2f%n", stosunek(FabrykaIstot::goblin, FabrykaIstot::losowyBohater, true)));
        s.append(String.format("POJEDYNKI ork      %.2f%n", stosunek(FabrykaIstot::ork, FabrykaIstot::losowyBohater, true)));
        s.append(String.format("POJEDYNKI ogr      %.2f%n", stosunek(FabrykaIstot::ogr, FabrykaIstot::losowyBohater, true)));
        s.append(String.format("POJEDYNKI rycerz   %.2f%n", stosunek(FabrykaIstot::rycerz, FabrykaIstot::losowyPotwor, false)));
        s.append(String.format("POJEDYNKI lucznik  %.2f%n", stosunek(FabrykaIstot::lucznik, FabrykaIstot::losowyPotwor, false)));
        s.append(String.format("POJEDYNKI zlodziej %.2f%n", stosunek(FabrykaIstot::zlodziej, FabrykaIstot::losowyPotwor, false)));
        return s.toString();
    }

    @Test
    void wartoscZeWzoruOdpowiadaSileWWalce() {
        double goblin = stosunek(FabrykaIstot::goblin, FabrykaIstot::losowyBohater, true);
        double ork = stosunek(FabrykaIstot::ork, FabrykaIstot::losowyBohater, true);
        double ogr = stosunek(FabrykaIstot::ogr, FabrykaIstot::losowyBohater, true);
        double rycerz = stosunek(FabrykaIstot::rycerz, FabrykaIstot::losowyPotwor, false);
        double lucznik = stosunek(FabrykaIstot::lucznik, FabrykaIstot::losowyPotwor, false);
        System.out.printf("POJEDYNKI goblin %.2f · ork %.2f · ogr %.2f · rycerz %.2f · lucznik %.2f%n",
                goblin, ork, ogr, rycerz, lucznik);
        // Wzór jest wyrównany, gdy każdy rodzaj potwora jest wart w walce tyle samo względem swojej wyceny
        // (z dokładnością do 25%), i tak samo każdy walczący rodzaj bohatera.
        double[] potwory = {goblin, ork, ogr};
        double sredniaP = (goblin + ork + ogr) / 3;
        for (double p : potwory) {
            assertTrue(Math.abs(p / sredniaP - 1) < 0.25, "potwory nierówno wycenione: goblin " + goblin
                    + ", ork " + ork + ", ogr " + ogr);
        }
        double sredniaB = (rycerz + lucznik) / 2;
        assertTrue(Math.abs(rycerz / sredniaB - 1) < 0.25 && Math.abs(lucznik / sredniaB - 1) < 0.25,
                "bohaterowie nierówno wycenieni: rycerz " + rycerz + ", łucznik " + lucznik);
    }
}
