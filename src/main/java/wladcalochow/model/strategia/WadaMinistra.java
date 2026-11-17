package wladcalochow.model.strategia;

/** Słaba strona ministra. Każdy minister ma jedną. */
public enum WadaMinistra {
    CHCIWY("Chciwy", "Do pensji dolicza sobie dodatkowe 5% dziennego przychodu lochu."),
    PLOTKARZ("Plotkarz", "Rozpowiada o lochu: ataki bohaterów są o 10% silniejsze."),
    NIESLOWNY("Niesłowny", "Przekazuje raporty zwiadu byle jak: liczba bohaterów i cena łapówki są mniej dokładne."),
    NIELOJALNY("Nielojalny", "Każdego dnia z 3% szansą odchodzi bez ostrzeżenia, a urząd przejmuje darmowy stażysta.");

    private final String nazwa;
    private final String opis;

    WadaMinistra(String nazwa, String opis) {
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
