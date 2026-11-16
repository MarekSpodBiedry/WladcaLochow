package wladcalochow.model;

/** Coś, co wydarzyło się w grze. Model zwraca zdarzenia, interfejs je tylko pokazuje. */
public sealed interface Zdarzenie {

    enum Ton { NEUTRALNY, DOBRY, ZLY, ZLOTO, NAGLOWEK }

    String opis();

    Ton ton();

    record Informacja(String opis, Ton ton) implements Zdarzenie {
    }

    record NowaRunda(int numer) implements Zdarzenie {
        @Override
        public String opis() {
            return "Runda " + numer;
        }

        @Override
        public Ton ton() {
            return Ton.NAGLOWEK;
        }
    }

    record Atak(Istota kto, Istota cel, int obrazenia, boolean trafiony) implements Zdarzenie {
        @Override
        public String opis() {
            if (!trafiony) {
                return kto.getImie() + " atakuje: " + cel.getImie() + ", ale chybia.";
            }
            return kto.getImie() + " trafia: " + cel.getImie() + " za " + obrazenia + " obrażeń.";
        }

        @Override
        public Ton ton() {
            if (!trafiony) {
                return Ton.NEUTRALNY;
            }
            return cel instanceof Potwor ? Ton.ZLY : Ton.DOBRY;
        }
    }

    record Smierc(Istota kto, Istota zabojca, int zloto) implements Zdarzenie {
        @Override
        public String opis() {
            String lup = zloto > 0 ? " " + zabojca.getImie() + " zabiera " + zloto + " złota." : "";
            return kto.getImie() + " (" + kto.nazwaRodzaju() + ") pada." + lup;
        }

        @Override
        public Ton ton() {
            return kto instanceof Potwor ? Ton.ZLY : Ton.DOBRY;
        }
    }

    record Ucieczka(Istota kto) implements Zdarzenie {
        @Override
        public String opis() {
            return kto.getImie() + " (" + kto.nazwaRodzaju() + ") ucieka z pola walki.";
        }

        @Override
        public Ton ton() {
            return kto instanceof Potwor ? Ton.ZLY : Ton.DOBRY;
        }
    }

    record Ogluszenie(Istota kto) implements Zdarzenie {
        @Override
        public String opis() {
            return kto.getImie() + " zostaje ogłuszony i straci kolejkę.";
        }

        @Override
        public Ton ton() {
            return kto instanceof Potwor ? Ton.ZLY : Ton.DOBRY;
        }
    }

    record StrataKolejki(Istota kto) implements Zdarzenie {
        @Override
        public String opis() {
            return kto.getImie() + " jest ogłuszony i traci kolejkę.";
        }

        @Override
        public Ton ton() {
            return Ton.NEUTRALNY;
        }
    }

    record Kradziez(Istota kto, int kwota, boolean udana) implements Zdarzenie {
        @Override
        public String opis() {
            if (!udana) {
                return kto.getImie() + " próbuje okraść skarbiec, bez skutku.";
            }
            return kto.getImie() + " kradnie ze skarbca " + kwota + " złota!";
        }

        @Override
        public Ton ton() {
            return udana ? Ton.ZLY : Ton.NEUTRALNY;
        }
    }

    record KoniecWalki(boolean obronaUdana) implements Zdarzenie {
        @Override
        public String opis() {
            return obronaUdana ? "Loch obroniony!" : "Obrona przełamana!";
        }

        @Override
        public Ton ton() {
            return obronaUdana ? Ton.DOBRY : Ton.ZLY;
        }
    }
}
