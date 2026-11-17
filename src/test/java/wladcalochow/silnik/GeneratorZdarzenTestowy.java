package wladcalochow.silnik;

import java.util.List;
import java.util.Random;
import wladcalochow.model.Bohater;
import wladcalochow.model.Potwor;

/** Dostęp testów z innych pakietów do losowania bohaterów (metoda jest pakietowa). */
public final class GeneratorZdarzenTestowy {

    private GeneratorZdarzenTestowy() {
    }

    public static List<Bohater> bohaterowie(int dzien, List<Potwor> potwory, Random los) {
        return GeneratorZdarzen.losujBohaterow(dzien, 0, potwory, los);
    }
}
