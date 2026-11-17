package wladcalochow.model.strategia;

/** Postawa lochu wobec ataku bohaterów. */
public enum Postawa {
    PELNA_OBRONA("Pełna obrona", "Walczą wszystkie potwory, które nie kopią złota."),
    ODWOD("Odwód", "Jedna trzecia najbardziej doświadczonych wojowników czeka w ukryciu. Wchodzą, gdy pierwsza linia "
            + "stopnieje o jedną trzecią: uderzają z zaskoczenia, a bohaterowie tracą ducha. Zwiad bohaterów nie widzi "
            + "ukrytych, więc następne ataki są słabsze. Ceną jest słabsza pierwsza linia."),
    PRZEKUPSTWO("Przekupstwo", "Płacisz bohaterom, żeby odeszli bez walki. Gdy złota zabraknie, loch broni się normalnie.");

    private final String nazwa;
    private final String opis;

    Postawa(String nazwa, String opis) {
        this.nazwa = nazwa;
        this.opis = opis;
    }

    public String nazwa() {
        return nazwa;
    }

    public String opis() {
        return opis;
    }
}
