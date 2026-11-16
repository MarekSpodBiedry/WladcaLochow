package wladcalochow.model;

/** Najemnik Władcy. Oprócz statystyk istoty ma żołd, doświadczenie i przydział do kopalni. */
public abstract class Potwor extends Istota {

    public static final int MAX_POZIOM = 9;
    public static final double AWANS = 0.10;

    private int zold;
    private int lupDnia;
    private int doswiadczenie;
    private int doswiadczenieZWalki;
    private int poziom = 1;
    private boolean wKopalni;

    protected Potwor(String imie, int silaAtaku, int maxPunktyZycia, int maxMorale, int zold) {
        super(imie, silaAtaku, maxPunktyZycia, maxMorale, 0);
        if (zold < 0) {
            throw new IllegalArgumentException("Żołd nie może być ujemny");
        }
        this.zold = zold;
    }

    /** Siła i zdrowie sumarycznie: miara, którą kieruje się Minister Rambo. */
    public int moc() {
        return getSilaAtaku() + getMaxPunktyZycia();
    }

    /** Wartość złota wykopanego przez potwora w ciągu dnia pracy w kopalni. */
    public double wartoscWydobycia() {
        return (getSilaAtaku() + getPunktyZycia()) * procentMorale();
    }

    /**
     * Ile żołdu należy się potworowi dzisiaj. Łup z walki nie pomniejsza już żołdu: po walce trafia
     * do skarbca (wcześniej zostawał przy potworze i przepadał razem z nim, gdy potwór ginął).
     */
    public int nalezneDzis() {
        return zold;
    }

    public void otrzymajZold() {
        zmienMorale((int) Math.round(getMaxMorale() * 0.20));
        lupDnia = 0;
    }

    public void brakZoldu() {
        zmienMorale(-(int) Math.round(getMaxMorale() * 0.10));
        lupDnia = 0;
    }

    /** Potwór z morale poniżej 10%, który nie dostał żołdu, odchodzi z lochu. */
    public boolean chceOdejsc() {
        return procentMorale() < 0.10;
    }

    public void premia() {
        zmienMorale((int) Math.round(getMaxMorale() * 0.30));
    }

    @Override
    public int zabierzZloto(Istota ofiara) {
        int lup = super.zabierzZloto(ofiara);
        lupDnia += lup;
        doswiadczenieZWalki++;
        return lup;
    }

    /** Po przeżytej walce potwór zdobywa doświadczenie. Zwraca true, jeśli awansował. */
    public boolean podsumujWalke() {
        int zdobyte = 1 + doswiadczenieZWalki;
        doswiadczenieZWalki = 0;
        doswiadczenie += zdobyte;
        boolean awans = false;
        while (poziom < MAX_POZIOM && doswiadczenie >= progAwansu()) {
            doswiadczenie -= progAwansu();
            poziom++;
            wzmocnij(AWANS);
            zold = (int) Math.ceil(zold * (1 + AWANS));
            awans = true;
        }
        return awans;
    }

    private int progAwansu() {
        return 3 * poziom;
    }

    public int getZold() {
        return zold;
    }

    public int getLupDnia() {
        return lupDnia;
    }

    public int getPoziom() {
        return poziom;
    }

    public boolean czyWKopalni() {
        return wKopalni;
    }

    public void setWKopalni(boolean wKopalni) {
        this.wKopalni = wKopalni;
    }
}
