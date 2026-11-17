package wladcalochow.model.strategia;

/** Postawa lochu wobec ataku bohaterów. */
public enum Postawa {
    PELNA_OBRONA("Pełna obrona", "Walczą wszystkie potwory, które nie kopią złota."),
    ODWOD("Odwód", "Jedna trzecia najbardziej doświadczonych wojowników czeka z tyłu. Wchodzą, gdy pierwsza linia "
            + "stopnieje o połowę, i od razu uderzają. Chroni weteranów, ale pierwsza linia jest słabsza."),
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
