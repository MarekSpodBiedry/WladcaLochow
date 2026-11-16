package wladcalochow.model.strategia;

/** Postawa lochu wobec ataku bohaterów. */
public enum Postawa {
    PELNA_OBRONA("Pełna obrona", "Walczą wszystkie potwory, które nie kopią złota."),
    ODWOD("Odwód", "Potwory z morale poniżej 50% czekają w odwodzie i wchodzą dopiero, gdy pierwsza linia padnie."),
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
