import { CardReveal } from "./CardReveal";

export const GardenerScene: React.FC = () => {
  return (
    <CardReveal
      asset="tiny-gardener.png"
      cardNumber="CARD 2 OF 3"
      cardTitle="Tiny Gardener"
      rarity="EPIC"
      accent="#d6a2ff"
      backgroundStart="#311548"
      backgroundEnd="#182a2a"
    />
  );
};
