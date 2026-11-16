package wladcalochow.silnik;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZapisGryTest {

    private static Gra rozpoczeta(long ziarno) {
        Gra gra = Gra.nowa(ziarno);
        gra.rozpocznij(gra.getStartowiFinansow().get(0), gra.getStartowiPracy().get(0));
        return gra;
    }

    /**
     * Klasy zapisywane w pliku zapisu mają stały serialVersionUID. Bez niego każda zmiana w klasie
     * (nowe pole, nowa metoda) sprawia, że stare zapisy przestają się wczytywać.
     */
    @Test
    void klasyZapisuMajaStalyNumerWersji() throws Exception {
        Path klasy = Path.of(Gra.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<String> bezNumeru = new java.util.ArrayList<>();
        try (var pliki = Files.walk(klasy.resolve("wladcalochow"))) {
            for (Path plik : pliki.filter(p -> p.toString().endsWith(".class")).toList()) {
                String nazwa = klasy.relativize(plik).toString().replace('\\', '/').replace('/', '.')
                        .replaceAll("\\.class$", "");
                Class<?> klasa = Class.forName(nazwa, false, getClass().getClassLoader());
                boolean zapisywana = java.io.Serializable.class.isAssignableFrom(klasa) && !klasa.isRecord()
                        && !klasa.isEnum() && !klasa.isInterface() && !klasa.isAnonymousClass()
                        && !klasa.isSynthetic();
                if (zapisywana) {
                    try {
                        klasa.getDeclaredField("serialVersionUID");
                    } catch (NoSuchFieldException e) {
                        bezNumeru.add(nazwa);
                    }
                }
            }
        }
        assertTrue(bezNumeru.isEmpty(), "bez serialVersionUID: " + bezNumeru);
    }

    @Test
    void pustyFolderMaDziesiecWolnychSlotow(@TempDir Path folder) {
        List<ZapisGry.Slot> sloty = ZapisGry.sloty(folder.resolve("zapisy"));
        assertEquals(ZapisGry.SLOTY, sloty.size());
        assertTrue(sloty.stream().noneMatch(ZapisGry.Slot::zajety));
        assertEquals(1, ZapisGry.wolnySlot(sloty).orElseThrow().numer());
        assertTrue(ZapisGry.ostatniDoKontynuacji(sloty).isEmpty());
    }

    @Test
    void zapisTrafiaDoSwojegoSlotu(@TempDir Path folder) throws Exception {
        ZapisGry.zapisz(rozpoczeta(1), ZapisGry.plikSlotu(folder, 3));
        List<ZapisGry.Slot> sloty = ZapisGry.sloty(folder);
        assertTrue(sloty.get(2).zajety());
        assertTrue(sloty.get(2).doKontynuacji());
        assertEquals(1, ZapisGry.wolnySlot(sloty).orElseThrow().numer());
        assertEquals(3, ZapisGry.ostatniDoKontynuacji(sloty).orElseThrow().numer());
    }

    @Test
    void uszkodzonySlotJestZajetyAleNieDoKontynuacji(@TempDir Path folder) throws Exception {
        Files.createDirectories(folder);
        Files.writeString(ZapisGry.plikSlotu(folder, 1), "to nie jest zapis");
        ZapisGry.Slot slot = ZapisGry.sloty(folder).get(0);
        assertTrue(slot.zajety());
        assertTrue(slot.gra().isEmpty());
        assertFalse(slot.doKontynuacji());
        assertEquals(2, ZapisGry.wolnySlot(ZapisGry.sloty(folder)).orElseThrow().numer());
    }

    @Test
    void staryZapisTrafiaDoSlotuJedenIZostajeNaMiejscu(@TempDir Path katalog) throws Exception {
        Path stary = katalog.resolve("zapis.dat");
        ZapisGry.zapisz(rozpoczeta(2), stary);
        byte[] tresc = Files.readAllBytes(stary);
        Path folder = katalog.resolve("zapisy");

        ZapisGry.przeniesStaryZapis(folder, List.of(stary));

        assertTrue(Files.exists(stary), "stary zapis nie może zniknąć");
        assertArrayEquals(tresc, Files.readAllBytes(ZapisGry.plikSlotu(folder, 1)));
    }

    @Test
    void staryZapisNieNadpisujeIstniejacychSlotow(@TempDir Path katalog) throws Exception {
        Path stary = katalog.resolve("zapis.dat");
        ZapisGry.zapisz(rozpoczeta(2), stary);
        Path folder = katalog.resolve("zapisy");
        ZapisGry.zapisz(rozpoczeta(5), ZapisGry.plikSlotu(folder, 7));

        ZapisGry.przeniesStaryZapis(folder, List.of(stary));

        assertFalse(Files.exists(ZapisGry.plikSlotu(folder, 1)));
    }
}
