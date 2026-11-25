package wladcalochow.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import wladcalochow.model.Walka;
import wladcalochow.model.strategia.Rozkaz;
import wladcalochow.silnik.Gra;

/** Zapis każdej decyzji pozwala odtworzyć całą rozgrywkę: ziarno + lista decyzji = ten sam przebieg. */
class OdtwarzanieTest {

    private static void rozegrajDzien(Gra gra) {
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

    @Test
    void ziarnoIListaDecyzjiOdtwarzajaCalaGre() {
        long ziarno = 42;
        Gra gra = Gra.nowa(ziarno);
        gra.rozpocznij(gra.getStartowiFinansow().get(1), gra.getStartowiPracy().get(2));
        Random bot = new Random(7);
        String[] pomysly = {"POSTAWA ODWOD", "POSTAWA PRZEKUPSTWO", "POSTAWA PELNA_OBRONA", "LIMIT 50", "LIMIT 100",
            "ULEPSZENIE KOPALNIA", "ULEPSZENIE PULAPKI", "ULEPSZENIE ZAMEK_SKARBCA", "PREMIA P1", "ZWOLNIJ P2",
            "SZPITAL P1", "MINISTER MF1", "MINISTER MP1"};
        while (!gra.czyKoniecGry() && gra.getNumerDnia() < 60) {
            for (int i = 0; i < 3; i++) {
                try {
                    Rozkaz r = MostClaude.rozkaz(pomysly[bot.nextInt(pomysly.length)], gra);
                    if (gra.getDzien().maRozkazy() && gra.powodOdmowy(r) == null) {
                        gra.wykonajRozkaz(r);
                    }
                } catch (RuntimeException e) {
                    // polecenie bez sensu w tej chwili (np. nie ma potwora P2): pomijamy
                }
            }
            rozegrajDzien(gra);
        }
        List<String> decyzje = gra.getDecyzje();
        assertTrue(decyzje.size() > 20, "zapisano decyzje: " + decyzje.size());
        assertTrue(decyzje.get(0).startsWith("dzień 0: START MF2 MP3"), decyzje.get(0));

        // Odtworzenie wyłącznie z ziarna i listy decyzji.
        Gra kopia = Gra.nowa(ziarno);
        String[] start = decyzje.get(0).substring(decyzje.get(0).indexOf("START")).split(" ");
        kopia.rozpocznij(kopia.getStartowiFinansow().get(Integer.parseInt(start[1].substring(2)) - 1),
                kopia.getStartowiPracy().get(Integer.parseInt(start[2].substring(2)) - 1));
        List<String> reszta = new ArrayList<>(decyzje.subList(1, decyzje.size()));
        while (!kopia.czyKoniecGry() && kopia.getNumerDnia() < 60) {
            String dzis = "dzień " + kopia.getNumerDnia() + ": ";
            while (!reszta.isEmpty() && reszta.get(0).startsWith(dzis)) {
                kopia.wykonajRozkaz(MostClaude.rozkaz(reszta.remove(0).substring(dzis.length()), kopia));
            }
            rozegrajDzien(kopia);
        }
        assertTrue(reszta.isEmpty(), "wszystkie decyzje odtworzone");
        assertEquals(gra.getNumerDnia(), kopia.getNumerDnia());
        assertEquals(gra.getLoch().getSkarbiec().getZloto(), kopia.getLoch().getSkarbiec().getZloto());
        assertEquals(gra.getDziennik(), kopia.getDziennik(), "ten sam dziennik co do wpisu");
        assertEquals(decyzje, kopia.getDecyzje());
    }

    @Test
    void dziennikPokazujeKrotkieEtykiety() {
        assertEquals(new SkrotDziennika.Skrot("Złoto", "+306  · dochód 55 · kopalnia 251"),
                SkrotDziennika.skroc("Przypływ złota: +306 (dochód 55, kopalnia 251)."));
        assertEquals(new SkrotDziennika.Skrot("Atak", "4 bohaterów: łucznik ×2, złodziej ×2"),
                SkrotDziennika.skroc("Atak! Pod bramą stają: Sokoli Radek (Łucznik), Smukła Sława (Łucznik), "
                        + "Śliski Zenek (Złodziej), Kocur (Złodziej)."));
        assertEquals(new SkrotDziennika.Skrot("Zwiad", "1–6 bohaterów · łapówka 206 · SŁABSZY OD LOCHU"),
                SkrotDziennika.skroc("Zwiad: spodziewana liczba bohaterów: 1-6. Przekupienie ich kosztowałoby około "
                        + "206 złota. Ocena: słabszy od lochu."));
        assertEquals(new SkrotDziennika.Skrot("Kandydaci", "Chrzęst goblin 11 · Kark ork 25"),
                SkrotDziennika.skroc("Kandydaci do pracy: Chrzęst (Goblin, żołd 11), Kark (Ork, żołd 25)."));
        assertEquals(new SkrotDziennika.Skrot("Kandydaci", "Chrzęst goblin moc 54 żołd 11 · Kark ork moc 80 żołd 25"),
                SkrotDziennika.skroc("Kandydaci do pracy: Chrzęst (Goblin, moc 54, żołd 11), Kark (Ork, moc 80, żołd 25)."));
        assertEquals(new SkrotDziennika.Skrot("Rozkaz", "Kopalnia ma teraz poziom 1 · −70"),
                SkrotDziennika.skroc("Rozkaz: Kopalnia ma teraz poziom 1. Koszt: 70 złota."));
        assertEquals("", SkrotDziennika.skroc("Coś zupełnie nowego.").etykieta(), "nieznane zdanie zostaje bez zmian");
    }

    @Test
    void licznikPorazekToPorazkiBezWygranej() {
        SkrotDziennika.Skrot oczekiwany = new SkrotDziennika.Skrot("Porażki", "2 z 3 bez wygranej, przy 3. loch upada");
        assertEquals(oczekiwany, SkrotDziennika.skroc("Porażki bez wygranej: 2 z 3. Licznik zeruje tylko wygrana obrona. "
                + "Przy trzeciej loch upadnie."));
        assertEquals(oczekiwany, SkrotDziennika.skroc("Przegrane obrony z rzędu: 2 z 3. Przy trzeciej loch upadnie."),
                "dzienniki ze starych zapisów nadal dostają skrót");
        assertEquals("Koniec", SkrotDziennika.skroc("Trzecia porażka bez wygranej: bohaterowie zdobywają loch.").etykieta());
    }
}
