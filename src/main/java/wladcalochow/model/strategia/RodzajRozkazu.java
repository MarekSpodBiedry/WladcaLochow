package wladcalochow.model.strategia;

/** Grupa rozkazów, czyli jeden przycisk na pulpicie. W samouczku grupy odblokowują się po kolei. */
public enum RodzajRozkazu {
    ULEPSZENIA("Ulepszenia", 2),
    POSTAWA("Postawa", 3),
    POTWORY("Potwory", 4),
    BUDZET("Budżet", 4),
    MINISTROWIE("Ministrowie", 5);

    private final String nazwa;
    private final int dzienWSamouczku;

    RodzajRozkazu(String nazwa, int dzienWSamouczku) {
        this.nazwa = nazwa;
        this.dzienWSamouczku = dzienWSamouczku;
    }

    public String nazwa() {
        return nazwa;
    }

    /** Dzień, od którego ta grupa rozkazów jest dostępna w samouczku. */
    public int dzienWSamouczku() {
        return dzienWSamouczku;
    }
}
