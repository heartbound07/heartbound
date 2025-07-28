package com.app.heartbound.enums;

/**
 * Represents the four suits in a standard deck of playing cards.
 */
public enum Suit {
    SPADES("♠", 
        "<:spadesa:1399242685243592795>", "<:spades2:1399242571867488326>", "<:spades3:1399242588170616842>", "<:spades4:1399242601487536209>", 
        "<:spades5:1399242614741663764>", "<:spades6:1399242624946409583>", "<:spades7:1399242636803575849>", "<:spades8:1399242649025642618>", 
        "<:spades9:1399242661633982526>", "<:spades10:1399242672270610563>", "<:spadesj:1399242698795253861>", "<:spadesq:1399242738049749077>", "<:spadesk:1399242718915334184>"),
    HEARTS("♥", 
        "<:heartsa:1399242456460955718>", "<:hearts2:1399242340127866910>", "<:hearts3:1399242352287289458>", "<:hearts4:1399242361623810110>", 
        "<:hearts5:1399242370817593394>", "<:hearts6:1399242388496715806>", "<:hearts7:1399242406712315965>", "<:hearts8:1399242419932889229>", 
        "<:hearts9:1399242432511737951>", "<:hearts10:1399242444717166683>", "<:heartsj:1399242469429739590>", "<:heartsq:1399242522294751273>", "<:heartsk:1399242495358927030>"),
    DIAMONDS("♦", 
        "<:diamondsa:1399242260712783933>", "<:diamonds2:1399242123311841292>", "<:diamonds3:1399242137702240407>", "<:diamonds4:1399242152998998047>", 
        "<:diamonds5:1399242165208612885>", "<:diamonds6:1399242180090003560>", "<:diamonds7:1399242197437513728>", "<:diamonds8:1399242209487884349>", 
        "<:diamonds9:1399242220451790938>", "<:diamonds10:1399242246469058583>", "<:diamondsj:1399242273266471063>", "<:diamondsq:1399242308486037534>", "<:diamondsk:1399242289645092945>"),
    CLUBS("♣", 
        "<:clubsa:1399242037022162964>", "<:clubs2:1399241933775179826>", "<:clubs3:1399241945779535902>", "<:clubs4:1399241958228230336>", 
        "<:clubs5:1399241969124769912>", "<:clubs6:1399241979245625475>", "<:clubs7:1399241987743289466>", "<:clubs8:1399241996433887367>", 
        "<:clubs9:1399242007251128440>", "<:clubs10:1399242021746638889>", "<:clubsj:1399242051769471059>", "<:clubsq:1399242086074679346>", "<:clubsk:1399242074100076576>");

    private final String symbol;
    private final String[] cardUnicodes; // Ace through King

    Suit(String symbol, String... cardUnicodes) {
        this.symbol = symbol;
        this.cardUnicodes = cardUnicodes;
    }

    public String getSymbol() {
        return symbol;
    }

    /**
     * Get the Unicode representation for a card of this suit and rank.
     * @param rankValue 1-13 (Ace=1, Jack=11, Queen=12, King=13)
     * @return Unicode string for the card
     */
    public String getCardUnicode(int rankValue) {
        if (rankValue < 1 || rankValue > 13) {
            throw new IllegalArgumentException("Rank value must be between 1 and 13");
        }
        return cardUnicodes[rankValue - 1];
    }
} 