package com.app.heartbound.enums;

/**
 * Represents the four suits in a standard deck of playing cards.
 */
public enum Suit {
    SPADES("♠", 
        "<:spadesa:1387840222368235772>", "<:spades2:1387840226025803947>", "<:spades3:1387840229267865610>", "<:spades4:1387840233067778100>", 
        "<:spades5:1387840237358682345>", "<:spades6:1387840241175629845>", "<:spades7:1387840244627542056>", "<:spades8:1387840248301490207>", 
        "<:spades9:1387840252663828671>", "<:spades10:1387840256128323627>", "<:spadesj:1387840260255383674>", "<:spadesq:1387840266039459901>", "<:spadesk:1387840275514261614>"),
    HEARTS("♥", 
        "<:heatsa:1387840279779741776>", "<:hearts2:1387840283135443028>", "<:hearts3:1387840288508084244>", "<:hearts4:1387840292090155018>", 
        "<:hearts5:1387840295588331540>", "<:hearts6:1387840300931878993>", "<:hearts7:1387840310658207815>", "<:hearts8:1387840314835992616>", 
        "<:hearts9:1387840320280199168>", "<:hearts10:1387840324570714204>", "<:heartsj:1387840327775158346>", "<:heartsq:1387840332074586203>", "<:heartsk:1387840336444784720>"),
    DIAMONDS("♦", 
        "<:diamondsa:1387840340362526720>", "<:diamonds2:1387840343915102299>", "<:diamonds3:1387840347056378060>", "<:diamonds4:1387840350907006996>", 
        "<:diamonds5:1387840354820165783>", "<:diamonds6:1387840362281963520>", "<:diamonds7:1387840366559891527>", "<:diamonds8:1387840369873387562>", 
        "<:diamonds9:1387840373497532426>", "<:diamonds10:1387840377679122443>", "<:diamondsj:1387840382406230086>", "<:diamondsq:1387840385929183366>", "<:diamondsk:1387840389909844139>"),
    CLUBS("♣", 
        "<:clubsa:1387840394057875529>", "<:clubs2:1387840397610324121>", "<:clubs3:1387840401012166847>", "<:clubs4:1387840404442972310>", 
        "<:clubs5:1387840408024776744>", "<:clubs6:1387840412181598411>", "<:clubs7:1387840422466027771>", "<:clubs8:1387840425758294127>", 
        "<:clubs9:1387840429965447188>", "<:clubs10:1387849196824039606>", "<:clubsj:1387849289144991745>", "<:clubsq:1387849292391252071>", "<:clubsk:1387849296291827834>");

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