package wladcalochow.silnik;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import wladcalochow.model.DzienGry;
import wladcalochow.model.Loch;
import wladcalochow.model.MinisterFinansow;
import wladcalochow.model.MinisterPracy;
import wladcalochow.model.Potwor;
import wladcalochow.model.RamboMinisterPracy;
import wladcalochow.model.RozrzutnyMinisterFinansow;
import wladcalochow.model.strategia.CechaMinistra;
import wladcalochow.model.strategia.Postawa;
import wladcalochow.model.strategia.RaportZwiadu;
import wladcalochow.model.strategia.Rozkaz;
import wladcalochow.model.strategia.Ulepszenie;
import wladcalochow.model.strategia.WadaMinistra;

/**
 * Co zrobiłby ekspert: te same decyzje, które podejmuje automat-ekspert w symulacji balansu
 * (BalansSymulacjaTest), tylko zwrócone jako rady z powodem zamiast wykonane. Korzysta wyłącznie z tego,
 * co widzi gracz: zwiadu, skarbca, własnych potworów, kandydatów i ministrów.
 */
public final class Ekspert {

    /** Rada: rozkaz do wydania i krótki powód (do panelu). */
    public record Rada(Rozkaz rozkaz, String powod) {
    }

    private Ekspert() {
    }

    /** Ocena cechy i wady ministra z punktu widzenia doświadczonego gracza. */
    public static int ocena(CechaMinistra cecha, WadaMinistra wada) {
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

    /** Startowi ministrowie eksperta: Rozrzutny i Rambo z najlepszą parą cecha-wada. */
    public static MinisterFinansow startowyFinansow(List<MinisterFinansow> kandydaci) {
        return kandydaci.stream().filter(m -> m instanceof RozrzutnyMinisterFinansow)
                .max(Comparator.comparingInt(m -> ocena(m.getCecha(), m.getWada())))
                .orElse(kandydaci.get(0));
    }

    public static MinisterPracy startowyPracy(List<MinisterPracy> kandydaci) {
        return kandydaci.stream().filter(m -> m instanceof RamboMinisterPracy)
                .max(Comparator.comparingInt(m -> ocena(m.getCecha(), m.getWada())))
                .orElse(kandydaci.get(0));
    }

    /**
     * Rady na dzisiejszy poranek, w kolejności ważności. Rozkaz, którego gra by nie przyjęła (np. ta sama
     * postawa), nie trafia na listę. Ekspert wydaje tylko tyle rozkazów, ile zostało na dziś.
     */
    public static List<Rada> rady(Gra gra) {
        return rady(gra, true);
    }

    /** Rady eksperta; bez odwodu tylko do porównań w symulacji balansu. */
    public static List<Rada> rady(Gra gra, boolean zOdwodem) {
        List<Rada> rady = new ArrayList<>();
        Loch loch = gra.getLoch();
        DzienGry dzien = gra.getDzien();
        if (dzien == null || gra.czyKoniecGry()) {
            return rady;
        }
        int zloto = loch.getSkarbiec().getZloto();
        int wydane = 0;
        int pensje = loch.getMinisterFinansow().getPensja() + loch.getMinisterPracy().getPensja();
        int rezerwa = pensje + loch.dziennyKosztZoldu() * 2 / 3;
        RaportZwiadu raport = dzien.getRaport();
        List<Potwor> potwory = new ArrayList<>(loch.getListaPotworow());
        int obrona = DzienGry.wartoscOddzialu(potwory) * 2 / 3;

        // 1. Postawa: łapówka przy groźnym ataku (gdy stać), odwód przy słabszym, inaczej pełna obrona.
        Postawa postawa = Postawa.PELNA_OBRONA;
        String powodPostawy = "atak nie jest groźny";
        if (raport.maxBohaterow() > 0) {
            boolean groznie = raport.szacowanaSila() >= obrona * 0.8;
            // Środek widełek: świadome ryzyko. Płacenie tylko przy górnej granicy skracało grę eksperta (288 dni).
            if (groznie && (raport.lapowkaMin() + raport.lapowkaMax()) / 2 <= zloto - pensje) {
                postawa = Postawa.PRZEKUPSTWO;
                zloto -= raport.lapowkaMax();
                powodPostawy = "atak groźny, stać cię na łapówkę";
            } else if (groznie) {
                powodPostawy = "atak groźny, łapówka za droga";
            } else if (zOdwodem) {
                postawa = Postawa.ODWOD;
                powodPostawy = "atak słabszy: zasadzka w odwodzie";
            }
        } else {
            powodPostawy = "nikt nie atakuje";
        }
        dodaj(gra, rady, new Rozkaz.ZmianaPostawy(postawa), powodPostawy);

        // 2. Najtańszy poziom kopalni albo pułapek, potem zamek, jeśli zostanie na pensje.
        List<Ulepszenie> kolejnosc = new ArrayList<>(List.of(Ulepszenie.KOPALNIA, Ulepszenie.PULAPKI));
        kolejnosc.sort(Comparator.comparingInt(loch::poziom));
        kolejnosc.add(Ulepszenie.ZAMEK_SKARBCA);
        for (Ulepszenie u : kolejnosc) {
            Rozkaz zakup = new Rozkaz.ZakupUlepszenia(u);
            int koszt = gra.koszt(zakup);
            if (gra.powodOdmowy(zakup) == null && koszt <= zloto - pensje) {
                dodaj(gra, rady, zakup, "starczy jeszcze na pensje");
                zloto -= koszt;
                wydane += koszt;
                break;
            }
        }

        // 3. Rekrutacja bez limitu: decyduje minister.
        dodaj(gra, rady, new Rozkaz.LimitRekrutacji(100), "minister werbuje bez limitu");

        // 4. Stażysta psuje (wygaduje się, zwalnia przez pomyłkę): od razu najlepszy kandydat, najlepiej
        //    w ulubionym stylu. Potem zwykła wymiana ministra na lepszego w tym samym stylu.
        int wolne = zloto - rezerwa;
        boolean wymiana = false;
        MinisterFinansow mf = loch.getMinisterFinansow();
        if (mf.czyStazysta() && gra.getNastepnyMinisterFinansow() == null) {
            var najlepszy = dzien.getKandydaciFinansow().stream()
                    .max(Comparator.comparingInt((MinisterFinansow k) -> k instanceof RozrzutnyMinisterFinansow ? 1 : 0)
                            .thenComparingInt(k -> ocena(k.getCecha(), k.getWada())));
            if (najlepszy.isPresent()) {
                Rozkaz r = new Rozkaz.ZmianaMinistraFinansow(najlepszy.get());
                if (gra.koszt(r) <= wolne && dodaj(gra, rady, r, "stażysta psuje: zatrudnij kogoś")) {
                    wydane += gra.koszt(r);
                    wymiana = true;
                }
            }
        }
        // Stażystki kadr ekspert nie wymienia od razu: zwalnia rzadko, a rozkaz jest cenniejszy na szpital
        // albo postawę (pomiar 25.11: natychmiastowa wymiana skracała grę eksperta z 327 do 279 dni).
        for (MinisterFinansow k : wymiana ? List.<MinisterFinansow>of() : dzien.getKandydaciFinansow()) {
            Rozkaz r = new Rozkaz.ZmianaMinistraFinansow(k);
            if (k instanceof RozrzutnyMinisterFinansow && gra.getNastepnyMinisterFinansow() == null
                    && ocena(k.getCecha(), k.getWada()) >= ocena(mf.getCecha(), mf.getWada()) + 2
                    && gra.koszt(r) <= wolne) {
                wymiana = true;
                if (dodaj(gra, rady, r, "lepsza cecha i wada")) {
                    wydane += gra.koszt(r);
                }
                break;
            }
        }
        if (!wymiana) {
            MinisterPracy mp = loch.getMinisterPracy();
            for (MinisterPracy k : dzien.getKandydaciPracy()) {
                Rozkaz r = new Rozkaz.ZmianaMinistraPracy(k);
                if (k instanceof RamboMinisterPracy && gra.getNastepnyMinisterPracy() == null
                        && ocena(k.getCecha(), k.getWada()) >= ocena(mp.getCecha(), mp.getWada()) + 2
                        && gra.koszt(r) <= wolne) {
                    if (dodaj(gra, rady, r, "lepsza cecha i wada")) {
                        wydane += gra.koszt(r);
                    }
                    break;
                }
            }
        }

        // 5. Miejsce dla silniejszych: loch prawie pełny, a w kolejce ktoś 2× mocniejszy od najsłabszego.
        int najlepszyKandydat = dzien.getKandydaci().stream().mapToInt(Potwor::moc).max().orElse(0);
        Potwor najslabszy = potwory.stream().min(Comparator.comparingInt(Potwor::moc)).orElse(null);
        if (najslabszy != null && potwory.size() >= Loch.POJEMNOSC - 1 && najlepszyKandydat > najslabszy.moc() * 2) {
            if (dodaj(gra, rady, new Rozkaz.Zwolnienie(najslabszy), "w kolejce 2× silniejszy")) {
                potwory.remove(najslabszy);
            }
        }

        // 6. Szpital dla najsilniejszego rannego, gdy atak jest zapowiedziany.
        if (raport.maxBohaterow() > 0) {
            int naSzpital = loch.getSkarbiec().getZloto() - wydane - rezerwa;
            potwory.stream().filter(Potwor::czyRanny).max(Comparator.comparingInt(Potwor::moc)).ifPresent(r -> {
                Rozkaz szpital = new Rozkaz.Szpital(r);
                if (gra.powodOdmowy(szpital) == null && gra.koszt(szpital) <= naSzpital) {
                    dodaj(gra, rady, szpital, "najsilniejszy ranny, atak idzie");
                }
            });
        }

        // 7. Premia dla pierwszego potwora z morale poniżej połowy.
        for (Potwor p : potwory) {
            Rozkaz premia = new Rozkaz.Premia(p);
            if (p.procentMorale() < 0.5 && gra.powodOdmowy(premia) == null && gra.koszt(premia) <= zloto - rezerwa) {
                dodaj(gra, rady, premia, "morale poniżej połowy");
                break;
            }
        }
        return rady;
    }

    private static boolean dodaj(Gra gra, List<Rada> rady, Rozkaz rozkaz, String powod) {
        if (gra.powodOdmowy(rozkaz) != null) {
            return false;
        }
        rady.add(new Rada(rozkaz, powod));
        return true;
    }
}
