import { CardReveal } from "./CardReveal";

export const SleepyScene: React.FC = () => {
  return (
    <CardReveal
      asset="sleepy.png"
      cardNumber="CARD 1 OF 3"
      cardTitle="Sleepy"
      rarity="COMMON"
      accent="#f1d7a6"
      backgroundStart="#2b2447"
      backgroundEnd="#111a31"
    />
  );
};
