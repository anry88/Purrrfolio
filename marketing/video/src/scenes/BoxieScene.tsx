import { CardReveal } from "./CardReveal";

export const BoxieScene: React.FC = () => {
  return (
    <CardReveal
      asset="boxie.png"
      cardNumber="CARD 3 OF 3"
      cardTitle="Boxie"
      rarity="LEGENDARY"
      accent="#ffcd4d"
      backgroundStart="#432219"
      backgroundEnd="#1e1937"
    />
  );
};
