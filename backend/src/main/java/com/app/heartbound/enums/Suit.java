package com.app.heartbound.enums;

/**
 * Represents the four suits in a standard deck of playing cards.
 */
public enum Suit {
    SPADES("♠", 
        "<:spadesa:1251091362527313981>", "<:spades2:1236677538915287060>", "<:spades3:1236677540240953475>", "<:spades4:1236677541713150092>", 
        "<:spades5:1236677543147343986>", "<:spades6:1236677544544309371>", "<:spades7:1236677546066841631>", "<:spades8:1236677547350032385>", 
        "<:spades9:1236677726534897694>", "<:spades10:1236677728074338414>", "<:spadesj:1248604376772120586>", "<:spadesq:1248604383856427080>", "<:spadesk:1248604382442946660>"),
    HEARTS("♥", 
        "<:heatsa:1251092108534747157>", "<:hearts2:1236677771317481573>", "<:hearts3:1236677772739346432>", "<:hearts4:1236677774173802557>", 
        "<:hearts5:1236677775662780503>", "<:hearts6:1236677777131049021>", "<:hearts7:1236677778573885490>", "<:hearts8:1236677779697832077>", 
        "<:hearts9:1236677818679693433>", "<:hearts10:1236677820449689642>", "<:heartsj:1248604281481990196>", "<:heartsq:1248604283889258520>", "<:heartsk:1248604282891276400>"),
    DIAMONDS("♦", 
        "<:diamondsa:1251092139522134076>", "<:diamonds2:1236677868487049318>", "<:diamonds3:1236677870047461427>", "<:diamonds4:1236677875134894141>", 
        "<:diamonds5:1236677876640780298>", "<:diamonds6:1236677878259646505>", "<:diamonds7:1236677879757148295>", "<:diamonds8:1236677881212567714>", 
        "<:diamonds9:1236677906827313212>", "<:diamonds10:1236677908207108117>", "<:diamondsj:1248604281481990196>", "<:diamondsq:1248604244592955402>", "<:diamondsk:1248604242936332350>"),
    CLUBS("♣", 
        "<:clubsa:1251092173181554718>", "<:clubs2:1236758803769458758>", "<:clubs3:1236758805161971793>", "<:clubs4:1236758806751608883>", 
        "<:clubs5:1236758808429203560>", "<:clubs6:1236758810262110308>", "<:clubs7:1236758811725922355>", "<:clubs8:1236758813214900435>", 
        "<:clubs9:1236758853375496373>", "<:clubs10:1236758854843367535>", "<:clubsj:1248604202113040414>", "<:clubsq:1248604205141196800>", "<:clubsk:1248604203535044638>");

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