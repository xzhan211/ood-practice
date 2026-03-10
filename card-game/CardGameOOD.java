import java.util.*;

/**
 * General playing card framework for different card game rules.
 */
public class CardGameOOD {

    // ---------------- Enums ----------------
    enum Suit {
        SPADES, HEARTS, CLUBS, DIAMONDS, JOKER
    }

    enum Rank {
        TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT,
        NINE, TEN, JACK, QUEEN, KING, ACE, BLACK_JOKER, RED_JOKER
    }

    // ---------------- Core Model ----------------
    static class Card {
        private final Suit suit;
        private final Rank rank;

        public Card(Suit suit, Rank rank) {
            this.suit = suit;
            this.rank = rank;
        }

        public Suit getSuit() {
            return suit;
        }

        public Rank getRank() {
            return rank;
        }

        @Override
        public String toString() {
            return rank + " of " + suit;
        }
    }

    static class Deck {
        private final Deque<Card> cards = new ArrayDeque<>();

        public Deck(List<Card> cards) {
            this.cards.addAll(cards);
        }

        public static Deck standard52Deck() {
            List<Card> cards = new ArrayList<>();
            Suit[] suits = {Suit.SPADES, Suit.HEARTS, Suit.CLUBS, Suit.DIAMONDS};
            Rank[] ranks = {
                Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX,
                Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN,
                Rank.JACK, Rank.QUEEN, Rank.KING, Rank.ACE
            };

            for (Suit suit : suits) {
                for (Rank rank : ranks) {
                    cards.add(new Card(suit, rank));
                }
            }
            return new Deck(cards);
        }

        public static Deck standard54Deck() {
            List<Card> cards = new ArrayList<>();
            Deck base = standard52Deck();
            while (!base.isEmpty()) {
                cards.add(base.draw());
            }
            cards.add(new Card(Suit.JOKER, Rank.BLACK_JOKER));
            cards.add(new Card(Suit.JOKER, Rank.RED_JOKER));
            return new Deck(cards);
        }

        public void shuffle() {
            List<Card> list = new ArrayList<>(cards);
            Collections.shuffle(list);
            cards.clear();
            cards.addAll(list);
        }

        public Card draw() {
            if (cards.isEmpty()) {
                throw new NoSuchElementException("Deck is empty");
            }
            return cards.removeFirst();
        }

        public boolean isEmpty() {
            return cards.isEmpty();
        }

        public int size() {
            return cards.size();
        }
    }

    static class Hand {
        private final List<Card> cards = new ArrayList<>();

        public void addCard(Card card) {
            cards.add(card);
        }

        public List<Card> getCards() {
            return Collections.unmodifiableList(cards);
        }

        @Override
        public String toString() {
            return cards.toString();
        }
    }

    static class Player {
        private final String name;
        private final Hand hand = new Hand();

        public Player(String name) {
            this.name = name;
        }

        public void drawFrom(Deck deck) {
            hand.addCard(deck.draw());
        }

        public void drawFrom(Deck deck, int count) {
            for (int i = 0; i < count; i++) {
                drawFrom(deck);
            }
        }

        public Card playCard(int index) {
            List<Card> currentCards = new ArrayList<>(hand.cards);
            if (index < 0 || index >= currentCards.size()) {
                throw new IllegalArgumentException("Invalid card index");
            }
            Card card = currentCards.get(index);
            hand.cards.remove(index);
            return card;
        }

        public Hand getHand() {
            return hand;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name + " hand=" + hand;
        }
    }

    // ---------------- Rule Abstraction ----------------
    interface CardRule {
        int compare(Card c1, Card c2);
    }

    /**
     * Example rule:
     * rank order: 2 < 3 < ... < K < A
     * if same rank, compare suit: Clubs < Diamonds < Hearts < Spades
     */
    static class DefaultCardRule implements CardRule {
        private final Map<Rank, Integer> rankValue = new HashMap<>();
        private final Map<Suit, Integer> suitValue = new HashMap<>();

        public DefaultCardRule() {
            Rank[] rankOrder = {
                Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX,
                Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN,
                Rank.JACK, Rank.QUEEN, Rank.KING, Rank.ACE,
                Rank.BLACK_JOKER, Rank.RED_JOKER
            };

            for (int i = 0; i < rankOrder.length; i++) {
                rankValue.put(rankOrder[i], i);
            }

            suitValue.put(Suit.CLUBS, 0);
            suitValue.put(Suit.DIAMONDS, 1);
            suitValue.put(Suit.HEARTS, 2);
            suitValue.put(Suit.SPADES, 3);
            suitValue.put(Suit.JOKER, 4);
        }

        @Override
        public int compare(Card c1, Card c2) {
            int r1 = rankValue.getOrDefault(c1.getRank(), -1);
            int r2 = rankValue.getOrDefault(c2.getRank(), -1);

            if (r1 != r2) {
                return Integer.compare(r1, r2);
            }

            int s1 = suitValue.getOrDefault(c1.getSuit(), -1);
            int s2 = suitValue.getOrDefault(c2.getSuit(), -1);
            return Integer.compare(s1, s2);
        }
    }

    // ---------------- Service Layer ----------------
    static class CardGameService {
        private final CardRule rule;

        public CardGameService(CardRule rule) {
            this.rule = rule;
        }

        public Player comparePlayedCards(Player p1, Card c1, Player p2, Card c2) {
            int result = rule.compare(c1, c2);
            if (result > 0) return p1;
            if (result < 0) return p2;
            return null; // tie
        }

        public Card getHighestCard(Player player) {
            List<Card> cards = player.getHand().getCards();
            if (cards.isEmpty()) {
                return null;
            }

            Card best = cards.get(0);
            for (int i = 1; i < cards.size(); i++) {
                if (rule.compare(cards.get(i), best) > 0) {
                    best = cards.get(i);
                }
            }
            return best;
        }
    }

    // ---------------- Demo ----------------
    public static void main(String[] args) {
        Deck deck = Deck.standard54Deck();
        deck.shuffle();

        Player alice = new Player("Alice");
        Player bob = new Player("Bob");

        alice.drawFrom(deck, 3);
        bob.drawFrom(deck, 3);

        System.out.println(alice);
        System.out.println(bob);

        CardRule rule = new DefaultCardRule();
        CardGameService service = new CardGameService(rule);

        Card aliceCard = alice.playCard(0);
        Card bobCard = bob.playCard(0);

        System.out.println("Alice plays: " + aliceCard);
        System.out.println("Bob plays: " + bobCard);

        Player winner = service.comparePlayedCards(alice, aliceCard, bob, bobCard);
        if (winner == null) {
            System.out.println("Tie");
        } else {
            System.out.println("Winner is: " + winner.getName());
        }
    }
}